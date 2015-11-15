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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.gen5.api.extension.MethodArgumentResolver;
import org.junit.gen5.api.extension.TestExecutionContext;
import org.junit.gen5.engine.ExecutionRequest;
import org.junit.gen5.engine.TestDescriptor;

/**
 * @author Stefan Bechtold
 * @author Sam Brannen
 * @since 5.0
 */
public abstract class TestExecutionNode {

	private TestExecutionNode parent;

	private List<TestExecutionNode> children = new LinkedList<>();

	public void addChild(TestExecutionNode child) {
		this.children.add(child);
		child.parent = this;
	}

	public final TestExecutionNode getParent() {
		return this.parent;
	}

	public final List<TestExecutionNode> getChildren() {
		return Collections.unmodifiableList(this.children);
	}

	public abstract TestDescriptor getTestDescriptor();

	public abstract void execute(ExecutionRequest request, TestExecutionContext context);

	protected void executeChild(TestExecutionNode child, ExecutionRequest request, TestExecutionContext parentContext,
			Object testInstance) {

		TestExecutionContext childContext = createChildContext(child, parentContext, testInstance);
		child.execute(request, childContext);
	}

	private TestExecutionContext createChildContext(TestExecutionNode child, TestExecutionContext parentContext,
			Object testInstance) {
		return new DescriptorBasedTestExecutionContext(child.getTestDescriptor(), parentContext, testInstance);
	}

	public void executeBeforeEachTest(TestExecutionContext context, Object testInstance) {
	}

	public Throwable executeAfterEachTest(TestExecutionContext context, Object testInstance,
			Throwable previousException) {
		return null;
	}

	protected void invokeMethodInContext(Method method, TestExecutionContext context, Object target) {
		Set<MethodArgumentResolver> resolvers = context.getArgumentResolvers();
		MethodInvoker methodInvoker = new MethodInvoker(method, target, resolvers);
		methodInvoker.invoke(context);
	}

	protected Throwable invokeMethodInContextWithAggregatingExceptions(Method method, TestExecutionContext context,
			Object target, Throwable exceptionThrown) {
		try {
			invokeMethodInContext(method, context, target);
		}
		catch (Throwable ex) {
			Throwable currentException = ex;
			if (currentException instanceof InvocationTargetException) {
				currentException = ((InvocationTargetException) currentException).getTargetException();
			}

			if (exceptionThrown == null) {
				exceptionThrown = currentException;
			}
			else {
				exceptionThrown.addSuppressed(currentException);
			}
		}
		return exceptionThrown;
	}
}
