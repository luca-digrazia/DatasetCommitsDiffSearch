package com.googlecode.androidannotations.processing;

import static com.sun.codemodel.JExpr._null;
import static com.sun.codemodel.JExpr._this;
import static com.sun.codemodel.JExpr.invoke;
import static com.sun.codemodel.JExpr.ref;
import static com.sun.codemodel.JMod.PRIVATE;
import static com.sun.codemodel.JMod.PUBLIC;

import java.lang.annotation.Annotation;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;

import com.googlecode.androidannotations.annotations.FragmentArg;
import com.googlecode.androidannotations.helper.APTCodeModelHelper;
import com.googlecode.androidannotations.helper.AnnotationHelper;
import com.googlecode.androidannotations.helper.BundleHelper;
import com.googlecode.androidannotations.processing.EBeansHolder.Classes;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCatchBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JTryBlock;
import com.sun.codemodel.JVar;

public class FragmentArgProcessor implements DecoratingElementProcessor {

	private final APTCodeModelHelper helper = new APTCodeModelHelper();
	private final AnnotationHelper annotationHelper;

	public FragmentArgProcessor(ProcessingEnvironment processingEnv) {
		annotationHelper = new AnnotationHelper(processingEnv);
	}

	@Override
	public Class<? extends Annotation> getTarget() {
		return FragmentArg.class;
	}

	@Override
	public void process(Element element, JCodeModel codeModel, EBeanHolder holder) throws Exception {
		FragmentArg annotation = element.getAnnotation(FragmentArg.class);
		String argKey = annotation.value();
		String fieldName = element.getSimpleName().toString();

		if (argKey.isEmpty()) {
			argKey = fieldName;
		}

		TypeMirror elementType = element.asType();

		Classes classes = holder.classes();

		if (holder.fragmentArguments == null) {
			injectFragmentArguments(holder, codeModel);
		}

		JBlock ifContainsKey = holder.fragmentArgumentsNotNullBlock._if(JExpr.invoke(holder.fragmentArguments, "containsKey").arg(argKey))._then();

		JTryBlock containsKeyTry = ifContainsKey._try();

		JFieldRef argField = ref(fieldName);

		BundleHelper bundleHelper = new BundleHelper(annotationHelper, element);

		JInvocation restoreMethodCall = JExpr.invoke(holder.fragmentArguments, bundleHelper.getMethodNameToRestore()).arg(argKey);
		if (bundleHelper.restoreCallNeedCastStatement()) {

			JClass jclass = helper.typeMirrorToJClass(element.asType(), holder);
			JExpression castStatement = JExpr.cast(jclass, restoreMethodCall);
			containsKeyTry.body().assign(argField, castStatement);

			if (bundleHelper.restoreCallNeedsSuppressWarning()) {
				if (holder.fragmentArgumentsInjectMethod.annotations().size() == 0) {
					holder.fragmentArgumentsInjectMethod.annotate(SuppressWarnings.class).param("value", "unchecked");
				}
			}

		} else {
			containsKeyTry.body().assign(argField, restoreMethodCall);
		}

		JCatchBlock containsKeyCatch = containsKeyTry._catch(classes.CLASS_CAST_EXCEPTION);
		JVar exceptionParam = containsKeyCatch.param("e");

		JInvocation logError = classes.LOG.staticInvoke("e");

		logError.arg(holder.eBean.name());
		logError.arg("Could not cast argument to the expected type, the field is left to its default value");
		logError.arg(exceptionParam);

		containsKeyCatch.body().add(logError);

		{
			JMethod method = holder.fragmentBuilderClass.method(PUBLIC, holder.fragmentBuilderClass, fieldName);

			JClass paramClass = helper.typeMirrorToJClass(elementType, holder);
			JVar arg = method.param(paramClass, fieldName);
			// Assign
			method.body().invoke(holder.fragmentArgumentsBuilderField, bundleHelper.getMethodNameToSave()).arg(argKey).arg(arg);
			method.body()._return(_this());
		}

	}

	/**
	 * Adds call to injectFragmentArguments_() in onCreate and setIntent()
	 * methods.
	 */
	private void injectFragmentArguments(EBeanHolder holder, JCodeModel codeModel) {

		Classes classes = holder.classes();

		holder.fragmentArgumentsInjectMethod = holder.eBean.method(PRIVATE, codeModel.VOID, "injectFragmentArguments_");

		injectArgumentsOnInit(holder, classes.INTENT, holder.fragmentArgumentsInjectMethod);

		JBlock injectArgumentsBody = holder.fragmentArgumentsInjectMethod.body();

		holder.fragmentArguments = injectArgumentsBody.decl(classes.BUNDLE, "args_");
		holder.fragmentArguments.init(invoke("getArguments"));

		holder.fragmentArgumentsNotNullBlock = injectArgumentsBody._if(holder.fragmentArguments.ne(_null()))._then();
	}

	private void injectArgumentsOnInit(EBeanHolder holder, JClass intentClass, JMethod injectArgumentsMethod) {
		holder.init.body().invoke(injectArgumentsMethod);
	}
}
