package org.androidannotations.handler;

import com.sun.codemodel.*;
import org.androidannotations.annotations.ItemClick;
import org.androidannotations.model.AnnotationElements;
import org.androidannotations.validation.IsValid;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

import static com.sun.codemodel.JExpr.cast;
import static com.sun.codemodel.JExpr.invoke;

public class ItemClickHandler extends AbstractListenerHandler {

	public ItemClickHandler(ProcessingEnvironment processingEnvironment) {
		super(ItemClick.class, processingEnvironment);
	}

	@Override
	public boolean validate(Element element, AnnotationElements validatedElements) {
		IsValid valid = new IsValid();

		if (!super.validate(element, validatedElements)) {
			valid.invalidate();
		}

		ExecutableElement executableElement = (ExecutableElement) element;

		validatorHelper.returnTypeIsVoid(executableElement, valid);

		validatorHelper.param.zeroOrOneParameter(executableElement, valid);

		return valid.isValid();
	}

	@Override
	protected void makeCall(JBlock listenerMethodBody, JInvocation call, TypeMirror returnType) {
		listenerMethodBody.add(call);
	}

	@Override
	protected void processParameters(JMethod listenerMethod, JInvocation call, List<? extends VariableElement> parameters) {
		boolean hasItemParameter = parameters.size() == 1;

		JClass narrowAdapterViewClass = classes().ADAPTER_VIEW.narrow(codeModel().wildcard());
		JVar onItemClickParentParam = listenerMethod.param(narrowAdapterViewClass, "parent");
		listenerMethod.param(classes().VIEW, "view");
		JVar onItemClickPositionParam = listenerMethod.param(codeModel().INT, "position");
		listenerMethod.param(codeModel().LONG, "id");

		if (hasItemParameter) {
			VariableElement parameter = parameters.get(0);

			TypeMirror parameterType = parameter.asType();
			if (parameterType.getKind() == TypeKind.INT) {
				call.arg(onItemClickPositionParam);
			} else {
				String parameterTypeQualifiedName = parameterType.toString();
				call.arg(cast(refClass(parameterTypeQualifiedName), invoke(onItemClickParentParam, "getAdapter").invoke("getItem").arg(onItemClickPositionParam)));
			}
		}
	}

	@Override
	protected JMethod createListenerMethod(JDefinedClass listenerAnonymousClass) {
		return listenerAnonymousClass.method(JMod.PUBLIC, codeModel().VOID, "onItemClick");
	}

	@Override
	protected String getSetterName() {
		return "setOnItemClickListener";
	}

	@Override
	protected JClass getListenerClass() {
		return classes().ON_ITEM_CLICK_LISTENER;
	}

	@Override
	protected JType getViewClass() {
		return classes().ADAPTER_VIEW.narrow(codeModel().wildcard());
	}
}
