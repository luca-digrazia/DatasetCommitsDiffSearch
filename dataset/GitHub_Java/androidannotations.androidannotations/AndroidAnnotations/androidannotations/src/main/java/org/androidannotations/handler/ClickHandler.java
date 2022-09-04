package org.androidannotations.handler;

import com.sun.codemodel.*;
import org.androidannotations.annotations.Click;
import org.androidannotations.model.AnnotationElements;
import org.androidannotations.process.IsValid;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

public class ClickHandler extends AbstractListenerHandler {

	public ClickHandler(ProcessingEnvironment processingEnvironment) {
		super(Click.class, processingEnvironment);
	}

	@Override
	public boolean validate(Element element, AnnotationElements validatedElements) {
		IsValid valid = new IsValid();

		if (!super.validate(element, validatedElements)) {
			valid.invalidate();
		}

		ExecutableElement executableElement = (ExecutableElement) element;

		validatorHelper.returnTypeIsVoid(executableElement, valid);

		validatorHelper.param.zeroOrOneViewParameter(executableElement, valid);

		return valid.isValid();
	}

	@Override
	protected void makeCall(JBlock listenerMethodBody, JInvocation call, TypeMirror returnType) {
		listenerMethodBody.add(call);
	}

	@Override
	protected void processParameters(JMethod listenerMethod, JInvocation call, List<? extends VariableElement> parameters) {
		boolean hasItemParameter = parameters.size() == 1;

		JVar viewParam = listenerMethod.param(classes().VIEW, "view");

		if (hasItemParameter) {
			call.arg(viewParam);
		}
	}

	@Override
	protected JMethod createListenerMethod(JDefinedClass listenerAnonymousClass) {
		return listenerAnonymousClass.method(JMod.PUBLIC, codeModel().VOID, "onClick");
	}

	@Override
	protected String getSetterName() {
		return "setOnClickListener";
	}

	@Override
	protected JClass getListenerClass() {
		return classes().VIEW_ON_CLICK_LISTENER;
	}

}
