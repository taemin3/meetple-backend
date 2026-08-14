package com.meetple.backend.domain.meeting.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

class MeetingImageTest {

    @Test
    void objectKeyColumnAllowsNullForLegacyRows() throws Exception {
        Column column = MeetingImage.class
                .getDeclaredField("objectKey")
                .getAnnotation(Column.class);

        assertThat(column.nullable()).isTrue();
    }

    @Test
    void newMeetingImageRequiresObjectKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> MeetingImage.create(null, " ", 0))
                .withMessage("objectKey must not be blank");
    }
}
