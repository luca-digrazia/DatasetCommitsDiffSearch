package org.androidannotations.processing;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import org.androidannotations.annotations.Touch;
import org.androidannotations.rclass.IRClass;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;

/**
 * @author Rostislav Chekan
 * 
 */
public class TouchProcessor extends AbstractListenerProcessor {

	public TouchProcessor(ProcessingEnvironment processingEnv, IRClass rClass) {
		super(processingEnv, rClass);
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return Touch.class;
	}

	@Override
	protected void makeCall(JBlock listenerMethodBody, JInvocation call, TypeMirror returnType) {
		boolean returnMethodResult = returnType.getKind() != TypeKind.VOID;

		if (returnMethodResult) {
			listenerMethodBody._return(call);
		} else {
			listenerMethodBody.add(call);
			listenerMethodBody._return(JExpr.TRUE);
		}
	}

	@Override
	protected void processParameters(JMethod listenerMethod, JInvocation call, List<? extends VariableElement> parameters) {
		JVar viewParam = listenerMethod.param(classes.VIEW, "view");
		JVar eventParam = listenerMethod.param(classes.MOTION_EVENT, "event");
		boolean hasItemParameter = parameters.size() == 2;

		call.arg(eventParam);
		if (hasItemParameter) {
			call.arg(viewParam);
		}
	}

	@Override
	protected JMethod createListenerMethod(JDefinedClass listenerAnonymousClass) {
		return listenerAnonymousClass.method(JMod.PUBLIC, codeModel.BOOLEAN, "onTouch");
	}

	@Override
	protected String getSetterName() {
		return "setOnTouchListener";
	}

	@Override
	protected JClass getListenerClass() {
		return classes.VIEW_ON_TOUCH_LISTENER;
	}

}
