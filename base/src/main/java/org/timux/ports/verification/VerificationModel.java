package org.timux.ports.verification;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

class VerificationModel {

    private final Map<Element, HashSet<String>> inPortNames = new HashMap<>();
    private final Map<String, String> portSignatures = new HashMap<>();

    private final Reporter reporter;

    VerificationModel(Reporter reporter) {
        this.reporter = reporter;
    }

    String getRegisteredResponseType(String messageType) {
        return portSignatures.get(messageType);
    }

    void verifyAndRegisterInPortName(String portName, Element element) {
        HashSet<String> names = inPortNames.computeIfAbsent(element.getEnclosingElement(), k -> new HashSet<>());

        if (names.contains(portName)) {
            reporter.reportIssue(element, "duplicate IN port [%s]", portName);
        }

        names.add(portName);
    }

    void verifyAndRegisterResponseType(String messageType, String responseType, String portName, Element element) {
        verifyAndRegisterResponseType(messageType, responseType, portName, element, null);
    }

    void verifyAndRegisterResponseType(String messageType, String responseType, Element element, AnnotationMirror mirror) {
        verifyAndRegisterResponseType(messageType, responseType, "", element, mirror);
    }

    private void verifyAndRegisterResponseType(String messageType, String responseType, String portName, Element element, AnnotationMirror mirror) {
        String registeredResponseType = getRegisteredResponseType(messageType);

        if (registeredResponseType == null) {
            portSignatures.put(messageType, responseType);
        } else {
            if (!registeredResponseType.equals(responseType)) {
                String portNotice = portName.isEmpty()
                        ? ""
                        : String.format("port signatures do not match for OUT port [%s]: ", portName);

                String notice = portNotice + "message type '%s' cannot be mapped to both '%s' and '%s'";

                if (mirror == null) {
                    reporter.reportIssue(element, notice, messageType, registeredResponseType, responseType);
                } else {
                    reporter.reportIssue(element, mirror, notice, messageType, registeredResponseType, responseType);
                }
            }
        }
    }

    void verifyAndRegisterInPortResponseType(String messageType, String responseType, String portName, Element element) {
        String registeredResponseType = portSignatures.get(messageType);

        String registeredReturnTypeInfo = registeredResponseType != null
                ? " (" + registeredResponseType + ")"
                : "";

        if (responseType.equals("void")) {
            reporter.reportIssue(element, "IN port [%s] must return a value%s", portName, registeredReturnTypeInfo);
        } else if (responseType.equals(Void.class.getName())) {
            reporter.reportIssue(element, "IN port [%s] has inadmissible return type (%s)", portName, responseType);
        } else {
            verifyAndRegisterResponseType(messageType, responseType, portName, element);
        }
    }
}
