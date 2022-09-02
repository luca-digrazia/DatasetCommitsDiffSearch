/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.classfile;

import static com.oracle.truffle.espresso.nodes.BytecodeNode.resolveMethodCount;

import java.util.Objects;

import com.oracle.truffle.espresso.EspressoOptions;
import com.oracle.truffle.espresso.classfile.ConstantPool.Tag;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Descriptor;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public interface ClassMethodRefConstant extends MethodRefConstant {

    @Override
    default Tag tag() {
        return Tag.METHOD_REF;
    }

    final class Indexes extends MethodRefConstant.Indexes implements ClassMethodRefConstant, Resolvable {
        Indexes(int classIndex, int nameAndTypeIndex) {
            super(classIndex, nameAndTypeIndex);
        }

        /**
         * <h3>5.4.3.3. Method Resolution</h3>
         *
         * To resolve an unresolved symbolic reference from D to a method in a class C, the symbolic
         * reference to C given by the method reference is first resolved (§5.4.3.1). Therefore, any
         * exception that can be thrown as a result of failure of resolution of a class reference
         * can be thrown as a result of failure of method resolution. If the reference to C can be
         * successfully resolved, exceptions relating to the resolution of the method reference
         * itself can be thrown.
         *
         * When resolving a method reference:
         * <ol>
         *
         * <li>If C is an interface, method resolution throws an IncompatibleClassChangeError.
         *
         * <li>Otherwise, method resolution attempts to locate the referenced method in C and its
         * superclasses:
         * <ul>
         *
         * <li>If C declares exactly one method with the name specified by the method reference, and
         * the declaration is a signature polymorphic method (§2.9), then method lookup succeeds.
         * All the class names mentioned in the descriptor are resolved (§5.4.3.1).
         *
         * <li>The resolved method is the signature polymorphic method declaration. It is not
         * necessary for C to declare a method with the descriptor specified by the method
         * reference.
         *
         * <li>Otherwise, if C declares a method with the name and descriptor specified by the
         * method reference, method lookup succeeds.
         *
         * <li>Otherwise, if C has a superclass, step 2 of method resolution is recursively invoked
         * on the direct superclass of C.
         * </ul>
         *
         * <li>Otherwise, method resolution attempts to locate the referenced method in the
         * superinterfaces of the specified class C:
         * <ul>
         * <li>If the maximally-specific superinterface methods of C for the name and descriptor
         * specified by the method reference include exactly one method that does not have its
         * ACC_ABSTRACT flag set, then this method is chosen and method lookup succeeds.
         *
         * <li>Otherwise, if any superinterface of C declares a method with the name and descriptor
         * specified by the method reference that has neither its ACC_PRIVATE flag nor its
         * ACC_STATIC flag set, one of these is arbitrarily chosen and method lookup succeeds.
         *
         * <li>Otherwise, method lookup fails.
         * </ul>
         * </ol>
         *
         * A maximally-specific superinterface method of a class or interface C for a particular
         * method name and descriptor is any method for which all of the following are true:
         *
         * <ul>
         * <li>The method is declared in a superinterface (direct or indirect) of C.
         *
         * <li>The method is declared with the specified name and descriptor.
         *
         * <li>The method has neither its ACC_PRIVATE flag nor its ACC_STATIC flag set.
         *
         * <li>Where the method is declared in interface I, there exists no other maximally-specific
         * superinterface method of C with the specified name and descriptor that is declared in a
         * subinterface of I.
         * </ul>
         * The result of method resolution is determined by whether method lookup succeeds or fails:
         * <ul>
         * <li>If method lookup fails, method resolution throws a NoSuchMethodError.
         *
         * <li>Otherwise, if method lookup succeeds and the referenced method is not accessible
         * (§5.4.4) to D, method resolution throws an IllegalAccessError.
         *
         * Otherwise, let < E, L1 > be the class or interface in which the referenced method m is
         * actually declared, and let L2 be the defining loader of D.
         *
         * Given that the return type of m is Tr, and that the formal parameter types of m are Tf1,
         * ..., Tfn, then:
         *
         * If Tr is not an array type, let T0 be Tr; otherwise, let T0 be the element type (§2.4) of
         * Tr.
         *
         * For i = 1 to n: If Tfi is not an array type, let Ti be Tfi; otherwise, let Ti be the
         * element type (§2.4) of Tfi.
         *
         * The Java Virtual Machine must impose the loading constraints TiL1 = TiL2 for i = 0 to n
         * (§5.3.4).
         * </ul>
         * When resolution searches for a method in the class's superinterfaces, the best outcome is
         * to identify a maximally-specific non-abstract method. It is possible that this method
         * will be chosen by method selection, so it is desirable to add class loader constraints
         * for it.
         *
         * Otherwise, the result is nondeterministic. This is not new: The Java® Virtual Machine
         * Specification has never identified exactly which method is chosen, and how "ties" should
         * be broken. Prior to Java SE 8, this was mostly an unobservable distinction. However,
         * beginning with Java SE 8, the set of interface methods is more heterogenous, so care must
         * be taken to avoid problems with nondeterministic behavior. Thus:
         *
         * <ul>
         * <li>Superinterface methods that are private and static are ignored by resolution. This is
         * consistent with the Java programming language, where such interface methods are not
         * inherited.
         *
         * <li>Any behavior controlled by the resolved method should not depend on whether the
         * method is abstract or not.
         * </ul>
         * Note that if the result of resolution is an abstract method, the referenced class C may
         * be non-abstract. Requiring C to be abstract would conflict with the nondeterministic
         * choice of superinterface methods. Instead, resolution assumes that the run time class of
         * the invoked object has a concrete implementation of the method.
         */
        private static Method lookupMethod(Klass klass, Symbol<Name> name, Symbol<Signature> signature) {
            Method method = lookupClassMethod(klass, name, signature);
            if (method != null) {
                return method;
            }
            // FIXME(peterssen): Not implemented: If the maximally-specific superinterface methods
            // of C for the name and descriptor specified by the method reference include exactly
            // one method that does not have its ACC_ABSTRACT flag set, then this method is chosen
            // and method lookup succeeds.
            while (klass != null) {
                for (ObjectKlass i : klass.getSuperInterfaces()) {
                    method = lookupInterfaceMethod(i, name, signature);
                    if (method != null) {
                        return method;
                    }
                }
                klass = klass.getSuperKlass();
            }
            return null;
        }

        private static Method lookupInterfaceMethod(ObjectKlass interf, Symbol<Name> name, Symbol<Signature> signature) {
            for (Method m : interf.getDeclaredMethods()) {
                if (!m.isStatic() && !m.isPrivate() && name.equals(m.getName()) && signature.equals(m.getRawSignature())) {
                    return m;
                }
            }
            for (ObjectKlass i : interf.getSuperInterfaces()) {
                Method m = lookupInterfaceMethod(i, name, signature);
                if (m != null) {
                    return m;
                }
            }
            return null;
        }

        private static Method lookupClassMethod(Klass seed, Symbol<Name> name, Symbol<Signature> signature) {
            Method m = seed.lookupDeclaredMethod(name, signature);
            if (m != null) {
                return m;
            }
            if (seed.getSuperKlass() != null) {
                return lookupClassMethod(seed.getSuperKlass(), name, signature);
            }
            return null;
        }

        @Override
        public ResolvedConstant resolve(RuntimeConstantPool pool, int thisIndex, Klass accessingKlass) {
            resolveMethodCount.inc();
            Symbol<Name> holderKlassName = getHolderKlassName(pool);

            EspressoContext context = pool.getContext();
            Klass holderKlass = context.getRegistries().loadKlass(context.getTypes().fromName(holderKlassName), accessingKlass.getDefiningClassLoader());

            Meta meta = context.getMeta();
            if (holderKlass.isInterface()) {
                throw meta.throwExWithMessage(meta.IncompatibleClassChangeError, meta.toGuestString(getName(pool)));
            }

            Symbol<Name> name = getName(pool);
            Symbol<Signature> signature = getSignature(pool);

            Method method = lookupMethod(holderKlass, name, signature);
            if (method == null) {
                throw meta.throwExWithMessage(meta.NoSuchMethodError, meta.toGuestString(getName(pool)));
            }

            if (!MemberRefConstant.checkAccess(accessingKlass, holderKlass, method)) {
                System.err.println(EspressoOptions.INCEPTION_NAME + " Method access check of: " + method.getName() + " in " + holderKlass.getType() + " from " + accessingKlass.getType() + " throws IllegalAccessError");
                throw meta.throwExWithMessage(meta.IllegalAccessError, meta.toGuestString(getName(pool)));
            }

            return new Resolved(method);
        }

    }

    final class Resolved implements InterfaceMethodRefConstant, Resolvable.ResolvedConstant {
        private final Method resolved;

        Resolved(Method resolved) {
            this.resolved = Objects.requireNonNull(resolved);
        }

        @Override
        public Method value() {
            return resolved;
        }

        @Override
        public Symbol<Name> getHolderKlassName(ConstantPool pool) {
            throw EspressoError.shouldNotReachHere("Method already resolved");
        }

        @Override
        public Symbol<Name> getName(ConstantPool pool) {
            return resolved.getName();
        }

        @Override
        public Symbol<? extends Descriptor> getDescriptor(ConstantPool pool) {
            return resolved.getRawSignature();
        }
    }
}
