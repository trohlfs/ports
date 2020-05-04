package org.timux.ports;

import org.timux.ports.testapp.component.IntEvent;

class C {

    public int data;

    @In
    @AsyncPort
    private void onInt(IntEvent event) {
        this.data = event.getData();
    }
}
