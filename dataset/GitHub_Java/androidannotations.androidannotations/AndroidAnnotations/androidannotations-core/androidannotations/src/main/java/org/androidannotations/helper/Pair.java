/**
 * Copyright (C) 2010-2015 eBusiness Information, Excilys Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.androidannotations.helper;

public class Pair<A, B> {

	private final A objectA;
	private final B objectB;

	public Pair(A objectA, B objectB) {
		this.objectA = objectA;
		this.objectB = objectB;
	}

	@Override
	public String toString() {
		return "Pair [" + objectA + ", " + objectB + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (objectA == null ? 0 : objectA.hashCode());
		result = prime * result + (objectB == null ? 0 : objectB.hashCode());
		return result;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		Pair other = (Pair) obj;
		if (objectA == null) {
			if (other.objectA != null) {
				return false;
			}
		} else if (!objectA.equals(other.objectA)) {
			return false;
		}
		if (objectB == null) {
			if (other.objectB != null) {
				return false;
			}
		} else if (!objectB.equals(other.objectB)) {
			return false;
		}
		return true;
	}

}
