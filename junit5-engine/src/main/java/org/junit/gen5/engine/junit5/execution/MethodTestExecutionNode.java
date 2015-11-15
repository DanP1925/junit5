/*
 * Copyright 2015 the original author or authors.
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution and is available at
 *
 * http://www.eclipse.org/legal/epl-v10.html
 */

package org.junit.gen5.engine.junit5.execution;

import static org.junit.gen5.commons.util.AnnotationUtils.findAnnotatedMethods;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.gen5.api.After;
import org.junit.gen5.api.extension.TestExecutionContext;
import org.junit.gen5.commons.util.ReflectionUtils.MethodSortOrder;
import org.junit.gen5.engine.ExecutionRequest;
import org.junit.gen5.engine.junit5.descriptor.MethodTestDescriptor;
import org.opentestalliance.TestAbortedException;
import org.opentestalliance.TestSkippedException;

/**
 * @author Sam Brannen
 * @author Stefan Bechtold
 * @since 5.0
 */
class MethodTestExecutionNode extends TestExecutionNode {

	private final MethodTestDescriptor testDescriptor;

	private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

	MethodTestExecutionNode(MethodTestDescriptor testDescriptor) {
		this.testDescriptor = testDescriptor;
	}

	@Override
	public MethodTestDescriptor getTestDescriptor() {
		return this.testDescriptor;
	}

	@Override
	public void execute(ExecutionRequest request, TestExecutionContext context) {
		final Method testMethod = getTestDescriptor().getTestMethod();

		if (!this.conditionEvaluator.testEnabled(getTestDescriptor())) {
			// TODO Determine if we really need an explicit TestSkippedException.
			// TODO Provide a way for failed conditions to provide a detailed explanation
			// of why a condition failed (e.g., a text message).
			TestSkippedException testSkippedException = new TestSkippedException(
				String.format("Skipped test method [%s] due to failed condition", testMethod.toGenericString()));
			request.getTestExecutionListener().testSkipped(getTestDescriptor(), testSkippedException);

			// Abort execution of the test completely at this point.
			return;
		}

		request.getTestExecutionListener().testStarted(getTestDescriptor());
		Throwable exceptionThrown = null;

		try {
			executeBeforeMethods(context);
			invokeTestMethod(context.getTestMethod().get(), context);
		}
		catch (Throwable ex) {
			exceptionThrown = ex;
			if (ex instanceof InvocationTargetException) {
				exceptionThrown = ((InvocationTargetException) ex).getTargetException();
			}
		}
		finally {
			exceptionThrown = executeAfterMethods(context, exceptionThrown);
		}

		if (exceptionThrown != null) {
			if (exceptionThrown instanceof TestSkippedException) {
				request.getTestExecutionListener().testSkipped(getTestDescriptor(), exceptionThrown);
			}
			else if (exceptionThrown instanceof TestAbortedException) {
				request.getTestExecutionListener().testAborted(getTestDescriptor(), exceptionThrown);
			}
			else {
				request.getTestExecutionListener().testFailed(getTestDescriptor(), exceptionThrown);
			}
		}
		else {
			request.getTestExecutionListener().testSucceeded(getTestDescriptor());
		}
	}

	private void invokeTestMethod(Method method, TestExecutionContext context) {
		Object target = context.getTestInstance().get();
		invokeMethodInContext(method, context, target);
	}

	private void executeBeforeMethods(TestExecutionContext context) {
		getParent().executeBeforeEachTest(context, context.getTestInstance().get());
	}

	private Throwable executeAfterMethods(TestExecutionContext context, Throwable exceptionThrown) {

		Object target = context.getTestInstance().get();

		// TODO: A bit more complicated than before
		//return  getParent().executeAfterEachTest(context, target, exceptionThrown);

		for (Method method : findAnnotatedMethods(context.getTestClass().get(), After.class,
			MethodSortOrder.HierarchyUp)) {
			exceptionThrown = invokeMethodInContextWithAggregatingExceptions(method, context, target, exceptionThrown);
		}

		return exceptionThrown;
	}

}
