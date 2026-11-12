package ar.edu.um.progii.catalog.sync.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SyncOriginTest {

	@Test
	void keepsTheEventIdOfANotification() {

		SyncOrigin notificationOrigin = SyncOrigin.notification("catalog-event-7-4f3b");

		assertThat(notificationOrigin.getTrigger()).isEqualTo(SyncTrigger.NOTIFICATION);
		assertThat(notificationOrigin.getEventId()).isEqualTo("catalog-event-7-4f3b");

	}

	@Test
	void leavesTheEventIdEmptyWhenItIsNotANotification() {

		SyncOrigin scheduledOrigin = SyncOrigin.scheduled();

		assertThat(scheduledOrigin.getTrigger()).isEqualTo(SyncTrigger.SCHEDULED);
		assertThat(scheduledOrigin.getEventId()).isNull();

	}

	@Test
	void rejectsANotificationWithoutEventId() {

		assertThatThrownBy(() -> SyncOrigin.notification(" ")).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("A notification needs an event id");

	}
}
