// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.skyframe;

import java.io.Serializable;

/**
 * An exception that can be thrown by {@code writeObject} and {@code readObject} implementations of
 * {@link Serializable} objects to indicate that they cannot actually be serialized. Should be used
 * only as a shim: in the long run, all {@link Serializable} objects should actually be
 * serializable.
 */
public class NotSerializableRuntimeException extends RuntimeException {}
