package faang.school.notificationservice.listener;

import faang.school.notificationservice.events.Event;

public interface EventListener<T extends Event> {
    void listenEvent(T event);
}