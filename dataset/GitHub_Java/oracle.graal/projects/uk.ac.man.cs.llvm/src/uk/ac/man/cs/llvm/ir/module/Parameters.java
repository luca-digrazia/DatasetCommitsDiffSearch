/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*
 * Copyright (c) 2016 University of Manchester
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package uk.ac.man.cs.llvm.ir.module;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import uk.ac.man.cs.llvm.bc.ParserListener;
import uk.ac.man.cs.llvm.ir.attributes.Attribute;
import uk.ac.man.cs.llvm.ir.attributes.BuiltinAttribute;
import uk.ac.man.cs.llvm.ir.attributes.KeyAttribute;
import uk.ac.man.cs.llvm.ir.module.records.ParametersRecord;

public final class Parameters implements ParserListener {

    private List<List<Attribute>> groups = new ArrayList<>();

    public Parameters() {
    }

    public ParserListener groups() {
        return new GroupListener();
    }

    @Override
    public void record(long id, long[] args) {
        // No idea why this is used, legacy perhaps
    }

    private static class GroupListener implements ParserListener {

        GroupListener() {
        }

        @Override
        public void record(long id, long[] args) {
            if (id != ParametersRecord.GROUP_ENTRY.ordinal()) {
                System.out.println("  RECORD #" + id + " = " + Arrays.toString(args));
            }

            int entry = (int) args[0];

            // boolean isForMethod = args[1] == 0xffffffffL;

            List<Attribute> parameters = new ArrayList<>();

            int i = 2;

            while (i < args.length) {
                int v = (int) args[i++];
                if (v >= '0') {
                    StringBuilder token = new StringBuilder();
                    do {
                        token.append((char) v);
                        v = (int) args[i++];
                    } while (v != 0);
                    String attr = token.toString();

                    if (attr.matches("(\\d+|false|true)")) {
                        int last = parameters.size() - 1;
                        parameters.set(last, parameters.get(last).value(attr));
                    } else {
                        parameters.add(new KeyAttribute(attr));
                    }
                } else {
                    Attribute attr = BuiltinAttribute.lookup(v);
                    if (attr != null && attr.key() != null) {
                        parameters.add(attr);
                    }
                }
            }

            System.out.printf("  attributes #%d = { %s }%n", entry, parameters.toString().replaceAll("[,\\[\\]]", ""));
        }
    }
}
