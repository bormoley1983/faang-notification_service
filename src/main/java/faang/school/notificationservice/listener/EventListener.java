package faang.school.notificationservice.listener;

import faang.school.notificationservice.model.event.Event;

public interface EventListener<T extends Event> {
    void listenEvent(T event);
}