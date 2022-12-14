package com.googlecode.androidannotations.model;

import java.util.Set;

public interface Instruction {
	
	/**
	 * Returns a set of imports that are needed for the code generated by this instruction.
	 * Imports should be strings such as "java.lang.Object" or "java.lang.*".
	 */
	Set<String> getImports();
	
	/**
	 * Returns a set of static imports that are needed for the code generated by this instruction.
	 * Imports should be strings such as "java.lang.Object" or "java.lang.*".
	 */
	Set<String> getStaticImports();
	
	String generate();

}
