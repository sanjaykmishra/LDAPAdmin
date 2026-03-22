package com.ldapadmin.service;

import com.ldapadmin.auth.AuthPrincipal;
import com.ldapadmin.auth.PrincipalType;
import com.ldapadmin.dto.accessreview.DecisionDto;
import com.ldapadmin.entity.*;
import com.ldapadmin.entity.enums.CampaignStatus;
import com.ldapadmin.entity.enums.ReviewDecision;
import com.ldapadmin.exception.LdapAdminException;
import com.ldapadmin.ldap.LdapGroupService;
import com.ldapadmin.repository.AccessReviewDecisionRepository;
import com.ldapadmin.repository.AccessReviewGroupRepository;
import com.ldapadmin.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccessReviewDecisionServiceTest {

    @Mock private AccessReviewDecisionRepository decisionRepo;
    @Mock private AccessReviewGroupRepository groupRepo;
    @Mock private AccountRepository accountRepo;
    @Mock private LdapGroupService ldapGroupService;
    @Mock private AuditService auditService;

    private AccessReviewDecisionService service;

    private final UUID reviewerId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();
    private final AuthPrincipal reviewerPrincipal = new AuthPrincipal(PrincipalType.ADMIN, reviewerId, "reviewer");
    private final AuthPrincipal otherPrincipal = new AuthPrincipal(PrincipalType.ADMIN, otherUserId, "other");
    private final AuthPrincipal superadminPrincipal = new AuthPrincipal(PrincipalType.SUPERADMIN, UUID.randomUUID(), "superadmin");

    private Account reviewerAccount;
    private AccessReviewGroup group;
    private AccessReviewDecision decision;

    @BeforeEach
    void setUp() {
        service = new AccessReviewDecisionService(decisionRepo, groupRepo, accountRepo, ldapGroupService, auditService);

        reviewerAccount = new Account();
        reviewerAccount.setId(reviewerId);
        reviewerAccount.setUsername("reviewer");

        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(UUID.randomUUID());

        AccessReviewCampaign campaign = new AccessReviewCampaign();
        campaign.setId(UUID.randomUUID());
        campaign.setDirectory(dir);
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setAutoRevoke(false);

        group = new AccessReviewGroup();
        group.setId(UUID.randomUUID());
        group.setCampaign(campaign);
        group.setGroupDn("cn=admins,dc=test");
        group.setMemberAttribute("member");
        group.setReviewer(reviewerAccount);

        decision = new AccessReviewDecision();
        decision.setId(UUID.randomUUID());
        decision.setReviewGroup(group);
        decision.setMemberDn("uid=user1,dc=test");
        decision.setMemberDisplay("User One");
    }

    @Test
    void decide_confirm_setsDecisionAndAudits() {
        when(decisionRepo.findById(decision.getId())).thenReturn(Optional.of(decision));
        when(accountRepo.findById(reviewerId)).thenReturn(Optional.of(reviewerAccount));
        when(decisionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DecisionDto result = service.decide(decision.getId(), ReviewDecision.CONFIRM, "Looks good", reviewerPrincipal);

        assertThat(result.decision()).isEqualTo(ReviewDecision.CONFIRM);
        assertThat(result.comment()).isEqualTo("Looks good");
        assertThat(result.decidedByUsername()).isEqualTo("reviewer");
        verify(auditService).record(any(), any(), any(), any(), any());
    }

    @Test
    void decide_revokeWithAutoRevoke_removesFromLdap() {
        group.getCampaign().setAutoRevoke(true);
        when(decisionRepo.findById(decision.getId())).thenReturn(Optional.of(decision));
        when(accountRepo.findById(reviewerId)).thenReturn(Optional.of(reviewerAccount));
        when(decisionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DecisionDto result = service.decide(decision.getId(), ReviewDecision.REVOKE, "Access not needed", reviewerPrincipal);

        assertThat(result.decision()).isEqualTo(ReviewDecision.REVOKE);
        verify(ldapGroupService).removeMember(any(), eq("cn=admins,dc=test"), eq("member"), eq("uid=user1,dc=test"));
    }

    @Test
    void decide_nonReviewer_throwsAccessDenied() {
        when(decisionRepo.findById(decision.getId())).thenReturn(Optional.of(decision));

        assertThatThrownBy(() -> service.decide(decision.getId(), ReviewDecision.CONFIRM, null, otherPrincipal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void decide_superadmin_bypassesReviewerCheck() {
        when(decisionRepo.findById(decision.getId())).thenReturn(Optional.of(decision));
        when(accountRepo.findById(superadminPrincipal.id())).thenReturn(Optional.of(reviewerAccount));
        when(decisionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DecisionDto result = service.decide(decision.getId(), ReviewDecision.CONFIRM, null, superadminPrincipal);

        assertThat(result.decision()).isEqualTo(ReviewDecision.CONFIRM);
    }

    @Test
    void decide_inactiveCampaign_throwsException() {
        group.getCampaign().setStatus(CampaignStatus.CLOSED);
        when(decisionRepo.findById(decision.getId())).thenReturn(Optional.of(decision));

        assertThatThrownBy(() -> service.decide(decision.getId(), ReviewDecision.CONFIRM, null, reviewerPrincipal))
                .isInstanceOf(LdapAdminException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void listForReviewGroup_returnsDecisions() {
        when(groupRepo.findById(group.getId())).thenReturn(Optional.of(group));
        when(decisionRepo.findByReviewGroupId(group.getId())).thenReturn(List.of(decision));

        List<DecisionDto> results = service.listForReviewGroup(group.getId(), reviewerPrincipal);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).memberDn()).isEqualTo("uid=user1,dc=test");
    }
}
