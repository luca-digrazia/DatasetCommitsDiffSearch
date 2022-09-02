/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.interop;

import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ReflectionLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import java.util.function.Supplier;

/**
 * Encapsulates types of access to {@link TruffleObject}. If you want to expose your own objects to
 * foreign language implementations, you need to implement {@link TruffleObject} and its
 * {@link TruffleObject#getForeignAccess()} method. To create instance of <code>ForeignAccess</code>
 * , use one of the factory methods available in this class.
 *
 * @since 0.8 or earlier
 *
 * @deprecated This class was replaced and split up into several interop libraries. See the
 *             individual methods for details on replacements.
 */
@Deprecated
@SuppressWarnings("deprecation")
public final class ForeignAccess {
    private final Factory factory;
    private final Supplier<? extends RootNode> languageCheckSupplier;

    // still here for GraalVM intrinsics.
    @SuppressWarnings("unused") private final Thread initThread;

    static final boolean LEGACY_TO_LIBRARY_BRIDGE = true;

    private ForeignAccess(Factory faf) {
        this(null, faf);
    }

    private ForeignAccess(Supplier<? extends RootNode> languageCheckSupplier, Factory faf) {
        this.factory = faf;
        this.initThread = null;
        this.languageCheckSupplier = languageCheckSupplier;
        CompilerAsserts.neverPartOfCompilation("do not create a ForeignAccess object from compiled code");
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param baseClass the super class of all {@link TruffleObject}s handled by this factory (if
     *            <code>null</code> then the second interface must also implement {@link Factory})
     * @param factory the factory that handles access requests to {@link Message}s
     * @return new instance wrapping <code>factory</code>
     * @since 0.30
     * @deprecated use <code>@{@linkplain ExportLibrary}(ArrayLibrary.class)</code> on the receiver
     *             type instead to export interop messages.
     */
    @Deprecated
    public static ForeignAccess create(final Class<? extends TruffleObject> baseClass, final StandardFactory factory) {
        if (baseClass == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        return new ForeignAccess(new DelegatingFactory(baseClass, factory));
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param factory the factory that handles access requests to {@link Message}s
     * @param languageCheck a {@link RootNode} that performs the language check on receiver objects,
     *            can be <code>null</code>, but then the factory must also implement {@link Factory}
     *            interface
     * @return new instance wrapping <code>factory</code>
     * @since 0.30
     * @deprecated use <code>@{@linkplain ExportLibrary}(ArrayLibrary.class)</code> on the receiver
     *             type instead to export interop messages.
     */
    @Deprecated
    public static ForeignAccess create(final StandardFactory factory, final RootNode languageCheck) {
        if (languageCheck == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        return new ForeignAccess(
                        languageCheck == null ? null : new RootNodeSupplier(languageCheck),
                        new DelegatingFactory(null, factory));
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param factory the factory that handles access requests to {@link Message}s
     * @param languageCheckSupplier a {@link Supplier} of {@link RootNode} that performs the
     *            language check on receiver objects, can be <code>null</code>, but then the factory
     *            must also implement {@link Factory} interface
     * @return new instance wrapping <code>factory</code>
     * @since 1.0
     */
    public static ForeignAccess createAccess(final StandardFactory factory, final Supplier<? extends RootNode> languageCheckSupplier) {
        if (languageCheckSupplier == null) {
            Factory f = (Factory) factory;
            assert f != null;
        }
        return new ForeignAccess(languageCheckSupplier, new DelegatingFactory(null, factory));
    }

    /**
     * Creates new instance of {@link ForeignAccess} that delegates to provided factory.
     *
     * @param factory the factory that handles various access requests {@link Message}s.
     * @return new instance wrapping <code>factory</code>
     * @since 0.8 or earlier
     * @deprecated use <code>@{@linkplain ExportLibrary}(ArrayLibrary.class)</code> on the receiver
     *             type instead to export interop messages.
     */
    @Deprecated
    public static ForeignAccess create(Factory factory) {
        return new ForeignAccess(factory);
    }

    /**
     * Sends a {@link Message} to the foreign receiver object by executing the
     * {@link Message#createNode() foreign node}.
     *
     * @param foreignNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param arguments parameters for the receiver
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws InteropException if any error occurred while accessing the <code>receiver</code>
     *             object
     * @since 0.24
     * @deprecated use the specific {@link InteropLibrary} or use library {@link ReflectionLibrary
     *             reflection} instead.
     */
    @Deprecated
    public static Object send(Node foreignNode, TruffleObject receiver, Object... arguments) throws InteropException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE && foreignNode instanceof LegacyToLibraryNode) {
                return ((LegacyToLibraryNode) foreignNode).send(receiver, arguments);
            } else {
                return ((InteropAccessNode) foreignNode).execute(receiver, arguments);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        }
    }

    /**
     * Sends a {@link Message#READ READ message} to the foreign receiver object by executing the
     * <code> readNode </code>.
     *
     * @param readNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param identifier name of the property to be read
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>readNode</code>
     * @throws UnknownIdentifierException if the <code>receiver</code> does not allow reading a
     *             property for the given <code>identifier</code>
     * @since 0.24
     * @deprecated use {@link InteropLibrary#readMember(Object, String)} or
     *             {@link InteropLibrary#readArrayElement(Object, long)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendRead(Node readNode, TruffleObject receiver, Object identifier) throws UnknownIdentifierException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) readNode).sendRead(receiver, identifier);
            } else {
                return ((InteropAccessNode) readNode).execute(receiver, identifier);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends a {@link Message#WRITE WRITE message} to the foreign receiver object by executing the
     * <code> writeNode </code>.
     *
     * @param writeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param identifier name of the property to be written
     * @param value value to be written
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>writeNode</code>
     * @throws UnknownIdentifierException if the <code>receiver</code> does not allow writing a
     *             property for the given <code>identifier</code>
     * @throws UnsupportedTypeException if <code>value</code> has an unsupported type
     * @since 0.24
     * @deprecated use {@link InteropLibrary#writeMember(Object, String, Object)} or
     *             {@link InteropLibrary#writeArrayElement(Object, long, Object)} instead.
     */
    @Deprecated
    public static Object sendWrite(Node writeNode, TruffleObject receiver, Object identifier, Object value)
                    throws UnknownIdentifierException, UnsupportedTypeException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                ((LegacyToLibraryNode) writeNode).sendWrite(receiver, identifier, value);
                return value;
            } else {
                return ((InteropAccessNode) writeNode).execute(receiver, identifier, value);
            }
        } catch (UnknownIdentifierException | UnsupportedTypeException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends a {@link Message#REMOVE REMOVE message} to the foreign receiver object by executing the
     * <code> removeNode </code>.
     *
     * @param removeNode the node created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param identifier name of the property to be removed
     * @return <code>true</code> if the property was successfully removed, <code>false</code>
     *         otherwise
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>readNode</code>
     * @throws UnknownIdentifierException if the <code>receiver</code> does not allow removing a
     *             property for the given <code>identifier</code>
     * @since 0.32
     * @deprecated use {@link InteropLibrary#removeMember(Object, String)} or
     *             {@link InteropLibrary#removeArrayElement(Object, long)} instead.
     */
    @Deprecated
    public static boolean sendRemove(Node removeNode, TruffleObject receiver, Object identifier)
                    throws UnknownIdentifierException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) removeNode).sendRemove(receiver, identifier);
            } else {
                return (boolean) ((InteropAccessNode) removeNode).execute(receiver, identifier);
            }
        } catch (UnsupportedTypeException e) {
            // necessary for legacy support
            throw UnsupportedMessageException.create();
        } catch (UnsupportedMessageException | UnknownIdentifierException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#UNBOX UNBOX message} to the foreign receiver object by executing the
     * <code> unboxNode </code>.
     *
     * @param unboxNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>unboxNode</code>
     * @since 0.24
     * @deprecated use {@link InteropLibrary#asString(Object)},
     *             {@link InteropLibrary#asBoolean(Object)}, {@link InteropLibrary#asByte(Object)},
     *             {@link InteropLibrary#asShort(Object)}, {@link InteropLibrary#asInt(Object)},
     *             {@link InteropLibrary#asLong(Object)}, {@link InteropLibrary#asFloat(Object)} or
     *             {@link InteropLibrary#asDouble(Object)} instead. See {@link InteropLibrary} for
     *             an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendUnbox(Node unboxNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) unboxNode).sendUnbox(receiver);
            } else {
                return ((InteropAccessNode) unboxNode).execute(receiver);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#IS_POINTER IS_POINTER message} to the foreign receiver object by
     * executing the <code> isPointerNode </code>.
     *
     * @param isPointerNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.26
     * @deprecated use {@link InteropLibrary#isPointer(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendIsPointer(Node isPointerNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) isPointerNode).sendIsPointer(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isPointerNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#AS_POINTER AS_POINTER message} to the foreign receiver object by
     * executing the <code> asPointerNode </code>.
     *
     * @param asPointerNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return a raw 64bit pointer value
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>asPointerNode</code>
     * @since 0.26
     * @deprecated use {@link InteropLibrary#asPointer(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    public static long sendAsPointer(Node asPointerNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) asPointerNode).sendAsPointer(receiver);
            } else {
                return (long) ((InteropAccessNode) asPointerNode).execute(receiver);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#TO_NATIVE TO_NATIVE message} to the foreign receiver object by
     * executing the <code> toNativeNode </code>.
     *
     * @param toNativeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>toNativeNode</code>
     * @since 0.26
     * @deprecated use {@link InteropLibrary#toNative(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendToNative(Node toNativeNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) toNativeNode).sendToNative(receiver);
            } else {
                return ((InteropAccessNode) toNativeNode).execute(receiver);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an EXECUTE {@link Message} to the foreign receiver object by executing the
     * <code> executeNode </code>.
     *
     * @param executeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign function object to receive the message passed to
     *            {@link Message#createNode()} method
     * @param arguments arguments passed to the foreign function
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedTypeException if one of element of the <code>arguments</code> has an
     *             unsupported type
     * @throws ArityException if the <code>arguments</code> array does not contain the right number
     *             of arguments for the foreign function
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>executeNode</code>
     * @since 0.24
     * @deprecated use {@link InteropLibrary#execute(Object, Object...)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendExecute(Node executeNode, TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) executeNode).sendExecute(receiver, arguments);
            } else {
                return ((InteropAccessNode) executeNode).execute(receiver, arguments);
            }
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#IS_EXECUTABLE IS_EXECUTABLE message} to the foreign receiver object
     * by executing the <code> isExecutableNode </code>.
     *
     * @param isExecutableNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     * @deprecated use {@link InteropLibrary#isExecutable(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendIsExecutable(Node isExecutableNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) isExecutableNode).sendIsExecutable(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isExecutableNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#IS_INSTANTIABLE IS_INSTANTIABLE message} to the foreign receiver
     * object by executing the <code>isInstantiableNode</code>.
     *
     * @param isInstantiableNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.30
     * @deprecated use {@link InteropLibrary#isInstantiable(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendIsInstantiable(Node isInstantiableNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) isInstantiableNode).sendIsInstantiable(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isInstantiableNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an INVOKE {@link Message} to the foreign receiver object by executing the
     * <code> invokeNode </code>.
     *
     * @param invokeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign function object to receive the message passed to
     *            {@link Message#createNode()} method
     * @param arguments arguments passed to the foreign function
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedTypeException if one of element of the <code>arguments</code> has an
     *             unsupported type
     * @throws UnknownIdentifierException if the <code>receiver</code> does not have a property for
     *             the given <code>identifier</code> that can be invoked
     * @throws ArityException if the <code>arguments</code> array does not contain the right number
     *             of arguments for the foreign function
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>invokeNode</code>
     * @since 0.24
     * @deprecated use {@link InteropLibrary#invokeMember(Object, String, Object...)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendInvoke(Node invokeNode, TruffleObject receiver, String identifier, Object... arguments)
                    throws UnsupportedTypeException, ArityException, UnknownIdentifierException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) invokeNode).sendInvoke(receiver, identifier, arguments);
            } else {
                return ((InteropAccessNode) invokeNode).execute(receiver, identifier, arguments);
            }
        } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an NEW {@link Message} to the foreign receiver object by executing the
     * <code> newNode </code>.
     *
     * @param newNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign function object to receive the message passed to
     *            {@link Message#createNode()} method
     * @param arguments arguments passed to the foreign function
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedTypeException if one of element of the <code>arguments</code> has an
     *             unsupported type
     * @throws ArityException if the <code>arguments</code> array does not contain the right number
     *             of arguments for the foreign function
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>newNode</code>
     * @since 0.24
     * @deprecated use {@link InteropLibrary#instantiate(Object, Object...)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendNew(Node newNode, TruffleObject receiver, Object... arguments) throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) newNode).sendNew(receiver, arguments);
            } else {
                return ((InteropAccessNode) newNode).execute(receiver, arguments);
            }
        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#IS_NULL IS_NULL message} to the foreign receiver object by executing
     * the <code> isNullNode </code>.
     *
     * @param isNullNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     * @deprecated use {@link InteropLibrary#isNull(Object)} instead. See {@link InteropLibrary} for
     *             an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendIsNull(Node isNullNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) isNullNode).sendIsNull(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isNullNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#HAS_SIZE HAS_SIZE message} to the foreign receiver object by
     * executing the <code> hasSizeNode </code>.
     *
     * @param hasSizeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     * @deprecated use {@link InteropLibrary#hasArrayElements(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendHasSize(Node hasSizeNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) hasSizeNode).sendHasSize(receiver);
            } else {
                return (boolean) ((InteropAccessNode) hasSizeNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends a {@link Message#GET_SIZE GET_SIZE message} to the foreign receiver object by executing
     * the <code> getSizeNode </code>.
     *
     * @param getSizeNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @throws UnsupportedMessageException if the <code>receiver</code> does not support the
     *             {@link Message#createNode() message represented} by <code>getSizeNode</code>
     * @since 0.24
     * @deprecated use {@link InteropLibrary#getArraySize(Object)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static Object sendGetSize(Node getSizeNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) getSizeNode).sendGetSize(receiver);
            } else {
                return ((InteropAccessNode) getSizeNode).execute(receiver);
            }
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw e;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#IS_BOXED IS_BOXED message} to the foreign receiver object by
     * executing the <code> isNullNode </code>.
     *
     * @param isBoxedNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     * @deprecated use {@link InteropLibrary#isString(Object)},
     *             {@link InteropLibrary#isBoolean(Object)} or
     *             {@link InteropLibrary#isNumber(Object)} instead. See {@link InteropLibrary} for
     *             an overview of the new interop messages.
     */
    @Deprecated
    public static boolean sendIsBoxed(Node isBoxedNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) isBoxedNode).sendIsBoxed(receiver);
            } else {
                return (boolean) ((InteropAccessNode) isBoxedNode).executeOrFalse(receiver);
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends a {@link Message#KEY_INFO KEY_INFO message} to the foreign receiver object by executing
     * the <code>keyInfoNode</code>. If the object does not support the message, the presence of the
     * key is found by iteration over it's keys on a slow path and a default info is returned.
     *
     * @param keyInfoNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param identifier name of the property to get the info of.
     * @return an integer value with bit flags described at {@link KeyInfo}.
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.26
     * @deprecated for {@link InteropLibrary#hasMembers(Object) objects} use
     *             {@link InteropLibrary#isMemberReadable(Object, String)},
     *             {@link InteropLibrary#isMemberWritable(Object, String)},
     *             {@link InteropLibrary#isMemberInsertable(Object, String)},
     *             {@link InteropLibrary#isMemberRemovable(Object, String)} or
     *             {@link InteropLibrary#isMemberInternal(Object, String)} instead. For
     *             {@link InteropLibrary#hasArrayElements(Object) arras} use
     *             {@link InteropLibrary#isArrayElementReadable(Object, long)},
     *             {@link InteropLibrary#isArrayElementWritable(Object, long)},
     *             {@link InteropLibrary#isArrayElementInsertable(Object, long)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static int sendKeyInfo(Node keyInfoNode, TruffleObject receiver, Object identifier) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) keyInfoNode).sendKeyInfo(receiver, identifier);
            } else {
                return (Integer) send(keyInfoNode, receiver, identifier);
            }
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            try {
                TruffleObject keys = sendKeys(Message.KEYS.createNode(), receiver, true);
                Number sizeNumber = (Number) sendGetSize(Message.GET_SIZE.createNode(), keys);
                int size = sizeNumber.intValue();
                Node readNode = Message.READ.createNode();
                for (int i = 0; i < size; i++) {
                    Object key = sendRead(readNode, keys, i);
                    // identifier must not be null
                    if (identifier.equals(key)) {
                        return KeyInfo.READABLE | KeyInfo.MODIFIABLE;
                    }
                }
            } catch (UnsupportedMessageException | UnknownIdentifierException uex) {
            }
            try {
                boolean hasSize = sendHasSize(Message.HAS_SIZE.createNode(), receiver);
                if (hasSize && identifier instanceof Number) {
                    int id = ((Number) identifier).intValue();
                    if (id < 0 || id != ((Number) identifier).doubleValue()) {
                        // identifier is some wild double number
                        return 0;
                    }
                    Number sizeNumber = (Number) sendGetSize(Message.GET_SIZE.createNode(), receiver);
                    int size = sizeNumber.intValue();
                    if (id < size) {
                        return KeyInfo.READABLE | KeyInfo.MODIFIABLE;
                    }
                }
            } catch (UnsupportedMessageException uex) {
            }
            return 0;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends an {@link Message#HAS_KEYS HAS_KEYS message} to the foreign receiver object by
     * executing the <code>hasKeysNode</code>. If the object does not support the message, a
     * {@link Message#KEYS} message is sent to test the presence of keys.
     *
     * @param hasKeysNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return value, if any
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.30
     * @deprecated use {@link InteropLibrary#hasMembers(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public static boolean sendHasKeys(Node hasKeysNode, TruffleObject receiver) {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) hasKeysNode).sendHasKeys(receiver);
            } else {
                return (boolean) ((InteropAccessNode) hasKeysNode).execute(receiver);
            }
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            try {
                sendKeys(Message.KEYS.createNode(), receiver, true);
                return true;
            } catch (UnsupportedMessageException uex) {
                return false;
            }
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends a {@link Message#KEYS} message to the foreign receiver object.
     *
     * @param keysNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @return return an instance of {@link TruffleObject} that responds to {@link Message#HAS_SIZE}
     *         and {@link Message#GET_SIZE} and its 0 to {@link Message#GET_SIZE size - 1} indexes
     *         contain {@link String} names of the properties of the <code>receiver</code> object
     * @throws UnsupportedMessageException if the message isn't handled
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.24
     * @deprecated use {@link InteropLibrary#getMembers(Object)} instead. See {@link InteropLibrary}
     *             for an overview of the new interop messages.
     */
    @Deprecated
    public static TruffleObject sendKeys(Node keysNode, TruffleObject receiver) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) keysNode).sendKeys(receiver);
            } else {
                return (TruffleObject) send(keysNode, receiver);
            }
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw ex;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Sends a {@link Message#KEYS} message to the foreign receiver object, with a specification of
     * whether internal keys should be included in the result, or not.
     *
     * @param keysNode the createNode created by {@link Message#createNode()}
     * @param receiver foreign object to receive the message passed to {@link Message#createNode()}
     *            method
     * @param includeInternal <code>true</code> to include internal keys in the result,
     *            <code>false</code> to abandon them.
     * @return return an instance of {@link TruffleObject} that responds to {@link Message#HAS_SIZE}
     *         and {@link Message#GET_SIZE} and its 0 to {@link Message#GET_SIZE size - 1} indexes
     *         contain {@link String} names of the properties of the <code>receiver</code> object
     * @throws UnsupportedMessageException if the message isn't handled
     * @throws ClassCastException if the createNode has not been created by
     *             {@link Message#createNode()} method.
     * @since 0.26
     * @deprecated use {@link InteropLibrary#getMembers(Object, boolean)} instead. See
     *             {@link InteropLibrary} for an overview of the new interop messages.
     */
    @Deprecated
    public static TruffleObject sendKeys(Node keysNode, TruffleObject receiver, boolean includeInternal) throws UnsupportedMessageException {
        try {
            if (LEGACY_TO_LIBRARY_BRIDGE) {
                return ((LegacyToLibraryNode) keysNode).sendKeys(receiver, includeInternal);
            } else {
                return (TruffleObject) send(keysNode, receiver, includeInternal);
            }
        } catch (UnsupportedMessageException ex) {
            CompilerDirectives.transferToInterpreter();
            throw ex;
        } catch (InteropException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError("Unexpected exception caught.", e);
        }
    }

    /**
     * Read only access to foreign call arguments inside of a frame.
     *
     * @param frame the frame that was called via
     *            {@link #send(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) }
     * @return read-only list of parameters passed to the frame
     * @since 0.11
     * @deprecated without replacement. There is no longer any frame involved for interop calls.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public static List<Object> getArguments(Frame frame) {
        final Object[] arr = frame.getArguments();
        return com.oracle.truffle.api.interop.impl.ReadOnlyArrayList.asList(arr, 1, arr.length);
    }

    /**
     * The foreign receiver in the frame.
     *
     * @param frame the frame that was called via
     *            {@link #send(com.oracle.truffle.api.nodes.Node, com.oracle.truffle.api.interop.TruffleObject, java.lang.Object...) }
     * @return the receiver used when invoking the frame
     * @since 0.8 or earlier
     * @deprecated without replacement. There is no longer any frame involved for interop calls.
     */
    @Deprecated
    public static TruffleObject getReceiver(Frame frame) {
        return (TruffleObject) frame.getArguments()[InteropAccessNode.ARG0_RECEIVER];
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        Object f;
        if (factory instanceof DelegatingFactory) {
            f = ((DelegatingFactory) factory).factory;
        } else {
            f = factory;
        }
        return "ForeignAccess[" + f.getClass().getName() + "]";
    }

    @SuppressWarnings("deprecation")
    CallTarget access(Message message) {
        return factory.accessMessage(message);
    }

    CallTarget checkLanguage() {
        if (languageCheckSupplier != null) {
            RootNode languageCheck = languageCheckSupplier.get();
            return Truffle.getRuntime().createCallTarget(languageCheck);
        } else {
            return null;
        }
    }

    boolean canHandle(TruffleObject receiver) {
        return factory.canHandle(receiver);
    }

    /**
     * Interface of a factory that produces AST snippets that can access a foreign
     * {@code TruffleObject}. A Truffle language implementation accesses a {@code TruffleObject} via
     * a {@code Message}. The {@code TruffleObject} instance provides a {@link ForeignAccess}
     * instance (built via {@link #create(com.oracle.truffle.api.interop.ForeignAccess.Factory)})
     * that provides an AST snippet for a given {@link Message}. Rather than using this generic
     * {@code Factory}, consider implementing {@link StandardFactory} interface that captures the
     * set of standard messages each language should implement.
     *
     * @since 0.8 or earlier
     * @deprecated use {@link ExportLibrary} instead to export message implementations.
     */
    @Deprecated
    public interface Factory {

        /**
         * Checks whether provided {@link TruffleObject} can be accessed using AST snippets produced
         * by this {@link Factory}.
         *
         * @param obj the object to check
         * @return true, if the object can be processed
         * @since 0.8 or earlier
         */
        boolean canHandle(TruffleObject obj);

        /**
         * Provides an AST snippet to access a {@code TruffleObject}.
         *
         * @param tree the {@code Message} that represents the access to a {@code TruffleObject}.
         * @return the AST snippet for accessing the {@code TruffleObject}, wrapped as a
         *         {@code CallTarget}.
         * @since 0.8 or earlier
         */
        CallTarget accessMessage(Message tree);
    }

    /**
     * Specialized {@link Factory factory} that handles standard foreign {@link Message messages}.
     * This interface is updated with new access methods when new messages are added. All default
     * implementations return <code>null</code>.
     *
     * @since 0.30
     * @deprecated use {@link ExportLibrary} instead to export message implementations.
     */
    @Deprecated
    public interface StandardFactory {
        /**
         * Handles {@link Message#IS_NULL} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessIsNull() {
            return null;
        }

        /**
         * Handles {@link Message#IS_EXECUTABLE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessIsExecutable() {
            return null;
        }

        /**
         * Handles {@link Message#IS_INSTANTIABLE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessIsInstantiable() {
            return null;
        }

        /**
         * Handles {@link Message#IS_BOXED} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessIsBoxed() {
            return null;
        }

        /**
         * Handles {@link Message#HAS_SIZE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessHasSize() {
            return null;
        }

        /**
         * Handles {@link Message#GET_SIZE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessGetSize() {
            return null;
        }

        /**
         * Handles {@link Message#UNBOX} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessUnbox() {
            return null;
        }

        /**
         * Handles {@link Message#READ} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessRead() {
            return null;
        }

        /**
         * Handles {@link Message#WRITE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessWrite() {
            return null;
        }

        /**
         * Handles {@link Message#REMOVE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.32
         */
        default CallTarget accessRemove() {
            return null;
        }

        /**
         * Handles {@link Message#EXECUTE} messages.
         *
         * @param argumentsLength do not use, always 0
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessExecute(int argumentsLength) {
            return null;
        }

        /**
         * Handles {@link Message#INVOKE} messages.
         *
         * @param argumentsLength do not use, always 0
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessInvoke(int argumentsLength) {
            return null;
        }

        /**
         * Handles {@link Message#NEW} messages.
         *
         * @param argumentsLength do not use, always 0
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessNew(int argumentsLength) {
            return null;
        }

        /**
         * Handles {@link Message#HAS_KEYS} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessHasKeys() {
            return null;
        }

        /**
         * Handles request for access to a message not known in version 0.10. The parameter to the
         * returned {@link CallTarget} is going to be the object/receiver. The return value is
         * supposed to be a {@link TruffleObject} that represents an array (responds to
         * {@link Message#HAS_SIZE} and {@link Message#GET_SIZE} and its element represent
         * {@link String} names of properties of the receiver.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessKeys() {
            return null;
        }

        /**
         * Handles {@link Message#KEY_INFO} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessKeyInfo() {
            return null;
        }

        /**
         * Handles {@link Message#IS_POINTER} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessIsPointer() {
            return null;
        }

        /**
         * Handles {@link Message#AS_POINTER} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessAsPointer() {
            return null;
        }

        /**
         * Handles {@link Message#TO_NATIVE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessToNative() {
            return null;
        }

        /**
         * Handles request for access to a non-standard (unknown) message.
         *
         * @param unknown the message
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.30
         */
        default CallTarget accessMessage(Message unknown) {
            return null;
        }
    }

    /**
     * Specialized {@link Factory factory} that handles {@link Message messages} known as of release
     * 0.26 of the Truffle API.
     *
     * @since 0.26
     * @deprecated extended set of messages is now supported, consider implementing
     *             {@link StandardFactory}
     */
    @Deprecated
    public interface Factory26 {
        /**
         * Handles {@link Message#IS_NULL} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessIsNull();

        /**
         * Handles {@link Message#IS_EXECUTABLE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessIsExecutable();

        /**
         * Handles {@link Message#IS_BOXED} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessIsBoxed();

        /**
         * Handles {@link Message#HAS_SIZE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessHasSize();

        /**
         * Handles {@link Message#GET_SIZE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessGetSize();

        /**
         * Handles {@link Message#UNBOX} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessUnbox();

        /**
         * Handles {@link Message#READ} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessRead();

        /**
         * Handles {@link Message#WRITE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessWrite();

        /**
         * Handles {@link Message#EXECUTE} messages.
         *
         * @param argumentsLength do not use, always 0
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessExecute(int argumentsLength);

        /**
         * Handles {@link Message#INVOKE} messages.
         *
         * @param argumentsLength do not use, always 0
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessInvoke(int argumentsLength);

        /**
         * Handles {@link Message#NEW} messages.
         *
         * @param argumentsLength do not use, always 0
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessNew(int argumentsLength);

        /**
         * Handles request for access to a message not known in version 0.10. The parameter to the
         * returned {@link CallTarget} is going to be the object/receiver. The return value is
         * supposed to be a {@link TruffleObject} that represents an array (responds to
         * {@link Message#HAS_SIZE} and {@link Message#GET_SIZE} and its element represent
         * {@link String} names of properties of the receiver.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessKeys();

        /**
         * Handles {@link Message#KEY_INFO} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessKeyInfo();

        /**
         * Handles {@link Message#IS_POINTER} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        default CallTarget accessIsPointer() {
            return null;
        }

        /**
         * Handles {@link Message#AS_POINTER} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        default CallTarget accessAsPointer() {
            return null;
        }

        /**
         * Handles {@link Message#TO_NATIVE} message.
         *
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        default CallTarget accessToNative() {
            return null;
        }

        /**
         * Handles request for access to a message not known in version 0.18.
         *
         * @param unknown the message
         * @return call target to handle the message or <code>null</code> if this message is not
         *         supported
         * @since 0.26
         */
        CallTarget accessMessage(Message unknown);
    }

    @SuppressWarnings("deprecation")
    private static class DelegatingFactory implements Factory {
        private final Class<?> baseClass;
        private final StandardFactory factory;

        DelegatingFactory(Class<?> baseClass, StandardFactory factory) {
            this.baseClass = baseClass;
            this.factory = factory;
        }

        @Override
        public boolean canHandle(TruffleObject obj) {
            if (baseClass == null) {
                return ((Factory) factory).canHandle(obj);
            }
            return baseClass.isInstance(obj);
        }

        @Override
        public CallTarget accessMessage(Message msg) {
            return accessMessage(factory, msg);
        }

        private static CallTarget accessMessage(StandardFactory factory, Message msg) {
            if (msg instanceof KnownMessage) {
                switch (msg.hashCode()) {
                    case Execute.HASH:
                        return factory.accessExecute(0);
                    case Invoke.HASH:
                        return factory.accessInvoke(0);
                    case New.HASH:
                        return factory.accessNew(0);
                    case GetSize.HASH:
                        return factory.accessGetSize();
                    case HasKeys.HASH:
                        return factory.accessHasKeys();
                    case HasSize.HASH:
                        return factory.accessHasSize();
                    case IsBoxed.HASH:
                        return factory.accessIsBoxed();
                    case IsExecutable.HASH:
                        return factory.accessIsExecutable();
                    case IsInstantiable.HASH:
                        return factory.accessIsInstantiable();
                    case IsNull.HASH:
                        return factory.accessIsNull();
                    case Read.HASH:
                        return factory.accessRead();
                    case Unbox.HASH:
                        return factory.accessUnbox();
                    case Write.HASH:
                        return factory.accessWrite();
                    case Remove.HASH:
                        return factory.accessRemove();
                    case Keys.HASH:
                        return factory.accessKeys();
                    case KeyInfoMsg.HASH:
                        return factory.accessKeyInfo();
                    case IsPointer.HASH:
                        return factory.accessIsPointer();
                    case AsPointer.HASH:
                        return factory.accessAsPointer();
                    case ToNative.HASH:
                        return factory.accessToNative();
                }
            }
            return factory.accessMessage(msg);
        }
    }

    private static final class RootNodeSupplier implements Supplier<RootNode> {
        private final RootNode rootNode;

        RootNodeSupplier(RootNode rootNode) {
            assert rootNode != null : "The rootNode must be non null.";
            this.rootNode = rootNode;
        }

        @Override
        public RootNode get() {
            return (RootNode) rootNode.deepCopy();
        }
    }

    @SuppressWarnings("unused") private static final InteropAccessor ACCESSOR = new InteropAccessor();
}
