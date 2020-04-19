package org.timux.ports.testapp.component;

import org.timux.ports.Either;
import org.timux.ports.Event;
import org.timux.ports.In;
import org.timux.ports.Out;

public class B {

    @Out Event<IntEvent> intEvent;
    @Out Event<RuntimeException> runtimeExceptionEvent;

    public B() {
        System.out.println("B con");
    }

    @In void onInt(IntEvent event) {
        System.out.println("B received input: " + event.getData());
        runtimeExceptionEvent.trigger(new RuntimeException("Test exception with data " + event.getData()));
    }

    @In void onObject(ObjectEvent event) {
        System.out.println("B received input 2: " + event.getObject());
    }

    @In Double onShortRequest(ShortRequest request) {
        System.out.println("B received request: " + request.getData());
        intEvent.trigger(new IntEvent((int) request.getData() + 1));
        return request.getData() * 1.5;
    }

    @In Object onObjectRequest(ObjectRequest request) {
        return "blub(" + request.getObject() + ")";
    }

    @In Either<Boolean, String> onTestCommand(TestCommand command) {
        return Either.a(true);
    }
}