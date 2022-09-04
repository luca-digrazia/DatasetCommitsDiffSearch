/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data.type;

/**
 * Short data type.
 *
 * @author Haifeng Li
 */
public class ShortType implements DataType {

    /** Singleton instance. */
    static ShortType instance = new ShortType();

    /**
     * Private constructor for singleton design pattern.
     */
    private ShortType() {
    }

    @Override
    public boolean isShort() {
        return true;
    }

    @Override
    public String name() {
        return "short";
    }

    @Override
    public ID id() {
        return ID.Short;
    }

    @Override
    public String toString() {
        return "short";
    }

    @Override
    public Short valueOf(String s) {
        return Short.valueOf(s);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ShortType;
    }
}
