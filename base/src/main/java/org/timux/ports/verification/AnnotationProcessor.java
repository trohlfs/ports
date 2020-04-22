package org.timux.ports.verification;

import org.timux.ports.Queue;
import org.timux.ports.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.BiConsumer;

public class AnnotationProcessor extends AbstractProcessor {

    private final static String EVENT_TYPE = Event.class.getName();
    private final static String REQUEST_TYPE = Request.class.getName();

    private final static String STACK_TYPE = org.timux.ports.Stack.class.getName();
    private final static String QUEUE_TYPE = Queue.class.getName();

    private Reporter reporter;
    private VerificationModel verificationModel;
    private MethodCheckerVisitor methodCheckerVisitor;

    private final Set<String> unmodifiableSupportedAnnotationTypes;

    {
        Set<String> supportedAnnotationTypes = new HashSet<>();

        supportedAnnotationTypes.add(In.class.getName());
        supportedAnnotationTypes.add(Out.class.getName());
        supportedAnnotationTypes.add(Response.class.getName());
        supportedAnnotationTypes.add(SuccessResponse.class.getName());
        supportedAnnotationTypes.add(FailureResponse.class.getName());

        unmodifiableSupportedAnnotationTypes = Collections.unmodifiableSet(supportedAnnotationTypes);
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return unmodifiableSupportedAnnotationTypes;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
        this.reporter = new Reporter(processingEnv);
        this.verificationModel = new VerificationModel(reporter);
        this.methodCheckerVisitor = new MethodCheckerVisitor(reporter, verificationModel);
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        if (set.isEmpty()) {
            return true;
        }

        checkOutPort(roundEnvironment);
        checkInPorts(roundEnvironment);
        checkRequestTypes(roundEnvironment);

        return true;
    }

    private void checkOutPort(RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(Out.class)) {
            String portType = element.asType().toString();
            String portName = element.getSimpleName().toString();
            String portSignature = extractTypeParameter(portType, Object.class.getName());

            String messageType = portSignature.indexOf(',') >= 0
                    ? portSignature.substring(0, portSignature.indexOf(','))
                    : portSignature;

            String returnType = portSignature.indexOf(',') >= 0
                    ? portSignature.substring(portSignature.indexOf(',') + 1)
                    : void.class.getName();

            if (element.getModifiers().contains(Modifier.STATIC)) {
                reporter.reportIssue(element, "OUT port [%s] must not be static", portName);
            }

            if (!portType.startsWith(EVENT_TYPE + "<")
                    && !portType.equals(EVENT_TYPE)
                    && !portType.startsWith(REQUEST_TYPE + "<")
                    && !portType.equals(REQUEST_TYPE))
            {
                reporter.reportIssue(element, "type '%s' is not a valid OUT port type", portType);
                return;
            }

            if (returnType.equals(Void.class.getName())) {
                reporter.reportIssue(element, "OUT port [%s] has inadmissible return type (%s)", portName, returnType);
            } else {
                verificationModel.verifyAndRegisterResponseType(messageType, returnType, portName, element);
            }

            if (!messageType.equals(Object.class.getName())
                    && portType.startsWith(EVENT_TYPE)
                    && !messageType.endsWith("Event")
                    && !messageType.endsWith("Exception"))
            {
                String commandNote = messageType.endsWith("Command")
                        ? " (commands should be implemented via request ports)"
                        : "";

                reporter.reportIssue(element, "'%s' is not a valid event type%s", messageType, commandNote);
            } else {
                if (portType.startsWith(EVENT_TYPE) && (messageType.endsWith("Event") || messageType.endsWith("Exception"))) {
                    String correctName = PortNamer.toOutPortName(messageType);

                    if (!portName.equals(correctName)) {
                        reporter.reportIssue(element, "'%s' is not a valid OUT port name (should be '%s')", portName, correctName);
                    }
                }
            }

            if (portType.startsWith(REQUEST_TYPE) && !(messageType.endsWith("Request") || messageType.endsWith("Command"))) {
                reporter.reportIssue(element, "'%s' is not a valid request type", messageType);
            } else {
                if (portType.startsWith(REQUEST_TYPE) && (messageType.endsWith("Request") || messageType.endsWith("Command"))) {
                    String correctName = PortNamer.toOutPortName(messageType);

                    if (!portName.equals(correctName)) {
                        reporter.reportIssue(element, "'%s' is not a valid OUT port name (should be '%s')", portName, correctName);
                    }
                }
            }
        }
    }

    private void checkInPorts(RoundEnvironment roundEnvironment) {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(In.class)) {
            String portType = element.asType().toString();
            String portName = element.getSimpleName().toString();

            if (element.getModifiers().contains(Modifier.STATIC)) {
                reporter.reportIssue(element, "IN port [%s] must not be static", portName);
            }

            if (!element.getKind().isField()) {
                element.accept(methodCheckerVisitor, null);
                continue;
            }

            if (!portType.startsWith(STACK_TYPE + "<")
                    && !portType.equals(STACK_TYPE)
                    && !portType.startsWith(QUEUE_TYPE + "<")
                    && !portType.equals(QUEUE_TYPE))
            {
                reporter.reportIssue(element, "type '%s' is not a valid IN port type", portType);
            }
        }
    }

    private void checkRequestTypes(RoundEnvironment roundEnvironment) {
        forEachAnnotatedElementDo(roundEnvironment, Responses.class, this::processMultipleResponsesAnnotatedElement);
        forEachAnnotatedElementDo(roundEnvironment, Response.class, this::processSingleResponseAnnotatedElement);
        forEachAnnotatedElementDo(roundEnvironment, SuccessResponse.class, this::processSingleResponseAnnotatedElement);
        forEachAnnotatedElementDo(roundEnvironment, FailureResponse.class, this::processSingleResponseAnnotatedElement);

        verificationModel.verifyThatNoSuccessOrFailureResponseTypesStandAlone();
    }

    private void processMultipleResponsesAnnotatedElement(Element element, AnnotationMirror mirror) {
        String responsesString = getMirrorValue(mirror);

        String messageType = element.toString();
        String[] parts = responsesString.split("Response\\(");
        List<String> responseTypes = new ArrayList<>();

        Arrays.stream(parts)
                .skip(1)
                .forEach(part -> responseTypes.add(part.substring(0, part.indexOf(".class)"))));

        for (String responseType : responseTypes) {
            if (responseType.equals(Void.class.getName())) {
                reporter.reportIssue(element, mirror, "message type '%s' has inadmissible return type (%s)", messageType, responseType);
            }
        }

        if (responseTypes.size() > 3) {
            reporter.reportIssue(element, mirror, "too many response types for message type '%s' (max. 3 allowed)", messageType);
            return;
        }

        String eitherArguments = responseTypes.stream()
                .reduce((xs, x) -> xs + "," + x)
                .orElseThrow(IllegalStateException::new);

        String responseType = String.format("%s<%s>",
                responseTypes.size() == 2 ? Either.class.getName() : Either3.class.getName(),
                eitherArguments);

        verificationModel.verifyAndRegisterResponseType(messageType, responseType, element, mirror);
    }

    private void processSingleResponseAnnotatedElement(Element element, AnnotationMirror mirror) {
        boolean isSuccessResponse = mirror.getAnnotationType().toString().equals(SuccessResponse.class.getName());
        boolean isFailureResponse = mirror.getAnnotationType().toString().equals(FailureResponse.class.getName());
        boolean isRegularResponse = !isSuccessResponse && !isFailureResponse;

        String mirrorValue = getMirrorValue(mirror);

        String messageType = element.toString();
        String responseType = mirrorValue.substring(0, mirrorValue.lastIndexOf('.'));

        if (responseType.equals(Void.class.getName())) {
            reporter.reportIssue(element, mirror, "message type '%s' has inadmissible return type (%s)", messageType, responseType);
            return;
        }

        if (isRegularResponse) {
            verificationModel.verifyAndRegisterResponseType(messageType, responseType, element, mirror);
        }

        if (isSuccessResponse) {
            verificationModel.verifyAndRegisterSuccessResponseType(messageType, responseType, element, mirror);
        }

        if (isFailureResponse) {
            verificationModel.verifyAndRegisterFailureResponseType(messageType, responseType, element, mirror);
        }
    }

    private void forEachAnnotatedElementDo(
            RoundEnvironment roundEnvironment, Class<? extends Annotation> annotation, BiConsumer<Element, AnnotationMirror> action)
    {
        for (Element element : roundEnvironment.getElementsAnnotatedWith(annotation)) {
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                if (mirror.getAnnotationType().toString().equals(annotation.getName())) {
                    action.accept(element, mirror);
                }
            }
        }
    }

    private String getMirrorValue(AnnotationMirror mirror) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e : mirror.getElementValues().entrySet()) {
            if ("value".equals(e.getKey().getSimpleName().toString())) {
                return e.getValue().toString();
            }
        }

        throw new IllegalStateException("annotation value must not be empty (" + mirror.getAnnotationType().toString() + ")");
    }

    private static String extractTypeParameter(String type, String _default) {
        int genericStart = type.indexOf('<');
        int genericEnd = type.lastIndexOf('>');

        return genericStart < 0
                ? _default
                : type.substring(genericStart + 1, genericEnd);
    }
}