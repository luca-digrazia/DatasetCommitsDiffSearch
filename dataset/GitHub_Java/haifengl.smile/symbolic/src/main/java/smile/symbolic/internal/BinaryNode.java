/*******************************************************************************
 * Copyright (c) 2017 Ernest DeFoy
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

package smile.symbolic.internal;

/**
 * @author Ernest DeFoy
 */
abstract class BinaryNode extends Expression {

    protected Expression left;
    protected Expression right;
    protected BinaryOperator type;

    BinaryNode(Expression left, Expression right, BinaryOperator type) {

        this.left = left;
        this.right = right;
        this.type = type;
    }

    public String getType() {

        return type.toString();
    }

    @Override
    public Expression getLeftChild() {
        return left;
    }

    @Override
    public Expression getRightChild() {
        return right;
    }
}

