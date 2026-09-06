package com.careerplatform.ai.service;

import com.careerplatform.career.entity.CareerGoal;
import com.careerplatform.career.service.CareerService;
import com.careerplatform.ai.dto.job.JobDiscoveryRequest;
import com.careerplatform.common.exception.InvalidRequestException;
import com.careerplatform.common.exception.ResourceNotFoundException;
import com.careerplatform.profile.entity.Skill;
import com.careerplatform.profile.entity.UserSkill;
import com.careerplatform.profile.enums.Proficiency;
import com.careerplatform.profile.service.ProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobDiscoveryContextBuilderTest {
    private static final Long USER_ID = 17L;
    private static final Long GOAL_ID = 31L;

    @Mock
    private CareerService careerService;
    @Mock
    private ProfileService profileService;

    private JobDiscoveryContextBuilder builder;
    private CareerGoal goal;

    @BeforeEach
    void setUp() {
        builder = new JobDiscoveryContextBuilder(careerService, profileService);
        goal = new CareerGoal();
        goal.setId(GOAL_ID);
        goal.setUserId(USER_ID);
        goal.setTargetPosition("Backend engineer");
        goal.setTargetCity("Shanghai");
        goal.setTargetIndustry("Software");
        goal.setTargetCompanyPreference("Product teams");
        goal.setNotes("Prefer roles with production ownership.");
    }

    @Test
    void ownerGoalIsResolvedBeforeAnySkillReadAndContextIsBounded() {
        when(careerService.getGoal(GOAL_ID, USER_ID)).thenReturn(goal);
        List<ProfileService.UserSkillDetail> details = new ArrayList<>();
        for (int i = 1; i <= 35; i++) {
            UserSkill userSkill = new UserSkill();
            userSkill.setId((long) i);
            userSkill.setUserId(USER_ID);
            userSkill.setProficiency(Proficiency.PROFICIENT);
            Skill skill = new Skill();
            skill.setId((long) i);
            skill.setName("Skill " + i);
            details.add(new ProfileService.UserSkillDetail(userSkill, skill));
        }
        when(profileService.listUserSkills(USER_ID)).thenReturn(details);

        JobDiscoveryContextBuilder.DiscoveryContext context = builder.build(
                USER_ID, new JobDiscoveryRequest(GOAL_ID, "remote-friendly roles", null, 3));

        assertThat(context.careerGoal()).isSameAs(goal);
        assertThat(context.location()).isEqualTo("Shanghai");
        assertThat(context.maxCandidates()).isEqualTo(3);
        assertThat(context.skills()).hasSize(30);
        assertThat(context.skillNamesByKey()).hasSize(30)
                .containsEntry("SKILL_1", "Skill 1")
                .containsEntry("SKILL_30", "Skill 30");
        assertThat(context.contextTextLength()).isEqualTo(context.contextText().length())
                .isLessThanOrEqualTo(JobDiscoveryContextBuilder.MAX_CONTEXT_TEXT_LENGTH);
        assertThat(context.contextText()).contains("SKILL_1=Skill 1 (proficiency=PROFICIENT)")
                .contains("SKILL_30=Skill 30 (proficiency=PROFICIENT)")
                .contains("UNTRUSTED_JOB_DISCOVERY_CONTEXT_START");
        assertThat(context.warnings()).isNotEmpty();

        InOrder order = inOrder(careerService, profileService);
        order.verify(careerService).getGoal(GOAL_ID, USER_ID);
        order.verify(profileService).listUserSkills(USER_ID);
    }

    @Test
    void foreignGoalStopsBeforeProfileSkillsAndNeverCallsProviderDependency() {
        when(careerService.getGoal(GOAL_ID, USER_ID))
                .thenThrow(new ResourceNotFoundException("资源不存在"));

        assertThatThrownBy(() -> builder.build(
                USER_ID, new JobDiscoveryRequest(GOAL_ID, null, null, 5)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("资源不存在");

        verify(careerService).getGoal(GOAL_ID, USER_ID);
        verify(profileService, never()).listUserSkills(USER_ID);
        verifyNoInteractions(profileService);
    }

    @Test
    void directServiceValidationRejectsUnboundedInputsBeforeGoalLookup() {
        assertThatThrownBy(() -> builder.build(USER_ID,
                new JobDiscoveryRequest(GOAL_ID, "x".repeat(1_001), null, 5)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> builder.build(USER_ID,
                new JobDiscoveryRequest(GOAL_ID, null, "x".repeat(101), 5)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> builder.build(USER_ID,
                new JobDiscoveryRequest(GOAL_ID, null, null, 11)))
                .isInstanceOf(InvalidRequestException.class);
        verify(careerService, never()).getGoal(GOAL_ID, USER_ID);
    }
}
