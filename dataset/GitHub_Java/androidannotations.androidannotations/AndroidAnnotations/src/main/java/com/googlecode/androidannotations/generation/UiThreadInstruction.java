/*
 * Copyright 2010-2011 Pierre-Yves Ricau (py.ricau at gmail.com)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.googlecode.androidannotations.generation;

import java.util.List;

import com.googlecode.androidannotations.model.Instruction;

public class UiThreadInstruction implements Instruction {

	private static final String FORMAT = //
	"" + //
			"    @Override\n" + //
			"    protected void %s(%s) {\n" + //
			"        runOnUiThread(new Runnable() {\n" + //
			"            public void run() {\n" + //
			"                try {\n" + //
			"                    %s.super.%s(%s);\n" + //
			"                } catch (RuntimeException e) {\n" + //
			"                    android.util.Log.e(\"%s\", \"\", e);\n" + //
			"                }\n" + //
			"            }\n" + //
			"        });\n" + //
			"    }\n" + //
			"\n";

	private final String methodName;

	private final String className;

	private final List<String> methodArguments;

	private final List<String> methodParameters;

	public UiThreadInstruction(String className, String methodName, List<String> methodArguments, List<String> methodParameters) {
		this.className = className;
		this.methodName = methodName;
		this.methodArguments = methodArguments;
		this.methodParameters = methodParameters;
	}

	@Override
	public String generate() {

		StringBuilder arguments = new StringBuilder();
		boolean first = true;
		for(String argument : methodArguments) {
			if (first) {
				first = false;
			} else {
				arguments.append(", ");
			}
			arguments.append("final ").append(argument);
		}
		
		first = true;
		StringBuilder parameters = new StringBuilder();
		for(String parameter : methodParameters) {
			if (first) {
				first = false;
			} else {
				parameters.append(", ");
			}
			parameters.append(parameter);
		}

		return String.format(FORMAT, methodName, arguments.toString(), className, methodName, parameters.toString(), className);
	}

}
