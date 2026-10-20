package com.onlinejudge.submission.application;

import com.onlinejudge.submission.dto.StudentAiFeedbackLookupResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentAiFeedbackAsyncServiceTest {

    @Test
    void enqueueDoesNotStartDuplicateGenerationWhileSubmissionIsRunning() {
        StudentAiFeedbackService feedbackService = mock(StudentAiFeedbackService.class);
        StudentAiFeedbackAsyncService self = mock(StudentAiFeedbackAsyncService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StudentAiFeedbackAsyncService> selfProvider = mock(ObjectProvider.class);
        StudentAiFeedbackAsyncService service = new StudentAiFeedbackAsyncService(
                feedbackService,
                new ExternalModelFailureClassifier(),
                selfProvider
        );
        when(feedbackService.markGenerating(7L)).thenReturn(StudentAiFeedbackLookupResponse.builder()
                .status("GENERATING")
                .build());
        when(selfProvider.getObject()).thenReturn(self);

        service.enqueue(7L);
        service.enqueue(7L);

        verify(self, times(1)).generate(7L);
    }

    @Test
    void recoveredRunContinuesEvenWhenExistingFeedbackIsReady() {
        StudentAiFeedbackService feedbackService = mock(StudentAiFeedbackService.class);
        StudentAiFeedbackAsyncService self = mock(StudentAiFeedbackAsyncService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<StudentAiFeedbackAsyncService> selfProvider = mock(ObjectProvider.class);
        StudentAiFeedbackAsyncService service = new StudentAiFeedbackAsyncService(
                feedbackService,
                new ExternalModelFailureClassifier(),
                selfProvider
        );
        when(selfProvider.getObject()).thenReturn(self);

        service.enqueueRecovered(8L);

        verify(feedbackService, times(0)).markGenerating(8L);
        verify(self, times(1)).generate(8L);
    }
}
