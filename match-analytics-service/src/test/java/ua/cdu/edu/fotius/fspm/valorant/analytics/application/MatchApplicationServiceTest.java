package ua.cdu.edu.fotius.fspm.valorant.analytics.application;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.exception.MatchDomainException;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.Match;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchHistoryEntry;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model.MatchResult;
import ua.cdu.edu.fotius.fspm.valorant.analytics.domain.repository.MatchRepository;
import ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.client.RankCalculatorClient;
import ua.cdu.edu.fotius.fspm.valorant.analytics.infrastructure.client.RosterServiceClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MatchApplicationServiceTest {

    private static final String TOKEN = "Bearer jwt-token-for-ProGamer#EUW";

    private MatchRepository matchRepository;
    private RankCalculatorClient rankClient;
    private RosterServiceClient rosterClient;
    private MatchApplicationService service;

    @BeforeEach
    void setUp() {
        matchRepository = Mockito.mock(MatchRepository.class);
        rankClient = Mockito.mock(RankCalculatorClient.class);
        rosterClient = Mockito.mock(RosterServiceClient.class);
        service = new MatchApplicationService(matchRepository, rankClient, rosterClient);
    }

    private MatchApplicationService.SubmitMatchCommand validCommand() {
        return new MatchApplicationService.SubmitMatchCommand(
            "r-101", "p-001", "Ascent", true, 5, 22, 12, 5, 310, false
        );
    }

    @Test
    @DisplayName("GIVEN LOCKED roster WHEN submitMatch THEN verifies, calculates, archives, and saves in order")
    void testSubmitMatchOrchestrationOrder() {
        when(rosterClient.getRoster("r-101", TOKEN))
            .thenReturn(new RosterServiceClient.RosterResponse("r-101", "p-001", "Ascent", "LOCKED", null, null));
        when(rankClient.calculateRating(any()))
            .thenReturn(new RankCalculatorClient.RankCalculationResponse(25, "PROMOTED"));

        Match result = service.submitMatch(validCommand(), TOKEN);

        assertNotNull(result.getId());
        assertEquals(25, result.getRatingDelta().deltaRR());
        assertEquals("PROMOTED", result.getRatingDelta().tierStatus());

        var savedMatchCaptor = ArgumentCaptor.forClass(Match.class);
        InOrder inOrder = inOrder(rosterClient, rankClient, matchRepository);
        inOrder.verify(rosterClient).getRoster("r-101", TOKEN);
        inOrder.verify(rankClient).calculateRating(any());
        inOrder.verify(rosterClient).archiveRoster("r-101", TOKEN);
        inOrder.verify(matchRepository).save(savedMatchCaptor.capture());
        assertEquals("r-101", savedMatchCaptor.getValue().getRosterId());
        assertEquals("p-001", savedMatchCaptor.getValue().getPlayerStats().playerId());
        verify(rosterClient, never()).unarchiveRoster(any(), any());
    }

    @Test
    @DisplayName("GIVEN invalid roundMargin WHEN submitMatch THEN fails fast before any HTTP calls")
    void testDomainValidationRunsBeforeExternalCalls() {
        var invalidCmd = new MatchApplicationService.SubmitMatchCommand(
            "r-101", "p-001", "Ascent", true, 0, 22, 12, 5, 310, false
        );

        var ex = assertThrows(MatchDomainException.class, () -> service.submitMatch(invalidCmd, TOKEN));

        assertEquals("INVALID_ROUND_MARGIN", ex.getCode());
        verifyNoInteractions(rosterClient, rankClient, matchRepository);
    }

    @Test
    @DisplayName("GIVEN DRAFT or ARCHIVED roster WHEN submitMatch THEN throws appropriate domain error")
    void testRosterStatusChecks() {
        when(rosterClient.getRoster("r-101", TOKEN))
            .thenReturn(new RosterServiceClient.RosterResponse("r-101", "p-001", "Ascent", "DRAFT", null, null));
        assertEquals("ROSTER_NOT_LOCKED", assertThrows(
            MatchDomainException.class, () -> service.submitMatch(validCommand(), TOKEN)
        ).getCode());

        when(rosterClient.getRoster("r-101", TOKEN))
            .thenReturn(new RosterServiceClient.RosterResponse("r-101", "p-001", "Ascent", "ARCHIVED", null, null));
        assertEquals("ROSTER_ALREADY_ARCHIVED", assertThrows(
            MatchDomainException.class, () -> service.submitMatch(validCommand(), TOKEN)
        ).getCode());

        verifyNoInteractions(rankClient, matchRepository);
    }

    @Test
    @DisplayName("GIVEN rank calculator fails WHEN submitMatch THEN throws RANK_CALCULATION_FAILED without archiving")
    void testRankCalculationFailureDoesNotArchive() {
        when(rosterClient.getRoster("r-101", TOKEN))
            .thenReturn(new RosterServiceClient.RosterResponse("r-101", "p-001", "Ascent", "LOCKED", null, null));
        when(rankClient.calculateRating(any()))
            .thenThrow(new HttpClientResponseException("Bad stats", HttpResponse.status(HttpStatus.BAD_REQUEST)));

        var ex = assertThrows(MatchDomainException.class, () -> service.submitMatch(validCommand(), TOKEN));

        assertEquals("RANK_CALCULATION_FAILED", ex.getCode());
        verify(rosterClient, never()).archiveRoster(any(), any());
        verify(matchRepository, never()).save(any());
    }

    @Test
    @DisplayName("GIVEN archive returns 409 WHEN submitMatch THEN throws ROSTER_ALREADY_ARCHIVED and does not save")
    void testArchiveConflictDoesNotSaveMatch() {
        when(rosterClient.getRoster("r-101", TOKEN))
            .thenReturn(new RosterServiceClient.RosterResponse("r-101", "p-001", "Ascent", "LOCKED", null, null));
        when(rankClient.calculateRating(any()))
            .thenReturn(new RankCalculatorClient.RankCalculationResponse(25, "PROMOTED"));
        doThrow(new HttpClientResponseException("Conflict", HttpResponse.status(HttpStatus.CONFLICT)))
            .when(rosterClient).archiveRoster("r-101", TOKEN);

        var ex = assertThrows(MatchDomainException.class, () -> service.submitMatch(validCommand(), TOKEN));

        assertEquals("ROSTER_ALREADY_ARCHIVED", ex.getCode());
        verify(matchRepository, never()).save(any());
    }

    @Test
    @DisplayName("GIVEN DB save fails WHEN submitMatch THEN compensates by calling unarchiveRoster")
    void testDbFailureTriggersUnarchiveCompensation() {
        when(rosterClient.getRoster("r-101", TOKEN))
            .thenReturn(new RosterServiceClient.RosterResponse("r-101", "p-001", "Ascent", "LOCKED", null, null));
        when(rankClient.calculateRating(any()))
            .thenReturn(new RankCalculatorClient.RankCalculationResponse(20, "MAINTAINED"));
        doThrow(new RuntimeException("DB connection lost"))
            .when(matchRepository).save(any(Match.class));

        var ex = assertThrows(MatchDomainException.class, () -> service.submitMatch(validCommand(), TOKEN));

        assertEquals("PERSISTENCE_FAILED", ex.getCode());
        verify(rosterClient).unarchiveRoster(eq("r-101"), eq(TOKEN));
    }

    @Test
    @DisplayName("GIVEN playerId WHEN getMatchHistory THEN delegates to MatchRepository and rejects blank IDs")
    void testGetMatchHistoryDelegatesAndValidates() {
        var expected = List.of(new MatchHistoryEntry("m-1", "Ascent", MatchResult.WIN, 22, "PROMOTED"));
        when(matchRepository.findHistoryByPlayerId("p-001")).thenReturn(expected);

        assertEquals(expected, service.getMatchHistory(" p-001 "));
        verify(matchRepository).findHistoryByPlayerId("p-001");

        var ex = assertThrows(MatchDomainException.class, () -> service.getMatchHistory("   "));
        assertEquals("INVALID_PLAYER_ID", ex.getCode());
    }
}
