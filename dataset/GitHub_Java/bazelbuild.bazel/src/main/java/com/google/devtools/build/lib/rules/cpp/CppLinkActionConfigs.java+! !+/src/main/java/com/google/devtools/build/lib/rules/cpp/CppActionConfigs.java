// Copyright 2014 The Bazel Authors. All rights reserved.
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
package com.google.devtools.build.lib.rules.cpp;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import java.util.Set;

/**
 * A helper class for creating action_configs for the c++ link action.
 *
 * <p>TODO(b/30109612): Replace this with action_configs in the crosstool instead of putting it in
 * legacy features.
 */
public class CppLinkActionConfigs {

  /** A platform for linker invocations. */
  public static enum CppLinkPlatform {
    LINUX,
    MAC
  }

  public static String getCppLinkActionConfigs(
      CppLinkPlatform platform,
      Set<String> features,
      String cppLinkDynamicLibraryToolPath,
      boolean supportsEmbeddedRuntimes) {
    String cppDynamicLibraryLinkerTool = "";
    if (!features.contains("dynamic_library_linker_tool")) {
      cppDynamicLibraryLinkerTool =
          ""
              + "feature {"
              + "   name: 'dynamic_library_linker_tool'"
              + "   flag_set {"
              + "       action: 'c++-link-dynamic-library'"
              + "       flag_group {"
              + "           flag: '"
              + cppLinkDynamicLibraryToolPath
              + "'"
              + "       }"
              + "   }"
              + "}";
    }

    return Joiner.on("\n")
        .join(
            ImmutableList.of(
                "action_config {",
                "   config_name: 'c++-link-executable'",
                "   action_name: 'c++-link-executable'",
                "   tool {",
                "       tool_path: 'DUMMY_TOOL'",
                "   }",
                "   implies: 'symbol_counts'",
                "   implies: 'strip_debug_symbols'",
                "   implies: 'linkstamps'",
                "   implies: 'output_execpath_flags_executable'",
                "   implies: 'runtime_library_search_directories'",
                "   implies: 'library_search_directories'",
                "   implies: 'libraries_to_link'",
                "   implies: 'force_pic_flags'",
                "   implies: 'legacy_link_flags'",
                "   implies: 'linker_param_file'",
                "   implies: 'fission_support'",
                "}",
                "action_config {",
                "   config_name: 'c++-link-dynamic-library'",
                "   action_name: 'c++-link-dynamic-library'",
                "   tool {",
                "       tool_path: 'DUMMY_TOOL'",
                "   }",
                "   implies: 'build_interface_libraries'",
                "   implies: 'dynamic_library_linker_tool'",
                "   implies: 'symbol_counts'",
                "   implies: 'strip_debug_symbols'",
                "   implies: 'shared_flag'",
                "   implies: 'linkstamps'",
                "   implies: 'output_execpath_flags'",
                "   implies: 'runtime_library_search_directories'",
                "   implies: 'library_search_directories'",
                "   implies: 'libraries_to_link'",
                "   implies: 'legacy_link_flags'",
                "   implies: 'linker_param_file'",
                "   implies: 'fission_support'",
                "}",
                "action_config {",
                "   config_name: 'c++-link-static-library'",
                "   action_name: 'c++-link-static-library'",
                "   tool {",
                "       tool_path: 'DUMMY_TOOL'",
                "   }",
                "   implies: 'strip_debug_symbols'",
                "   implies: 'runtime_library_search_directories'",
                "   implies: 'library_search_directories'",
                "   implies: 'libraries_to_link'",
                "   implies: 'linker_param_file'",
                "}",
                "action_config {",
                "   config_name: 'c++-link-alwayslink-static-library'",
                "   action_name: 'c++-link-alwayslink-static-library'",
                "   tool {",
                "       tool_path: 'DUMMY_TOOL'",
                "   }",
                "   implies: 'strip_debug_symbols'",
                "   implies: 'runtime_library_search_directories'",
                "   implies: 'library_search_directories'",
                "   implies: 'libraries_to_link'",
                "   implies: 'linker_param_file'",
                "}",
                "action_config {",
                "   config_name: 'c++-link-pic-static-library'",
                "   action_name: 'c++-link-pic-static-library'",
                "   tool {",
                "       tool_path: 'DUMMY_TOOL'",
                "   }",
                "   implies: 'strip_debug_symbols'",
                "   implies: 'runtime_library_search_directories'",
                "   implies: 'library_search_directories'",
                "   implies: 'libraries_to_link'",
                "   implies: 'linker_param_file'",
                "}",
                "action_config {",
                "   config_name: 'c++-link-alwayslink-pic-static-library'",
                "   action_name: 'c++-link-alwayslink-pic-static-library'",
                "   tool {",
                "       tool_path: 'DUMMY_TOOL'",
                "   }",
                "   implies: 'strip_debug_symbols'",
                "   implies: 'runtime_library_search_directories'",
                "   implies: 'library_search_directories'",
                "   implies: 'libraries_to_link'",
                "   implies: 'linker_param_file'",
                "}",
                "feature {",
                "   name: 'build_interface_libraries'",
                "   flag_set {",
                "       expand_if_all_available: 'generate_interface_library'",
                "       action: 'c++-link-dynamic-library'",
                "       flag_group {",
                "           flag: '%{generate_interface_library}'",
                "           flag: '%{interface_library_builder_path}'",
                "           flag: '%{interface_library_input_path}'",
                "           flag: '%{interface_library_output_path}'",
                "       }",
                "   }",
                "}",
                // Order of feature declaration matters, cppDynamicLibraryLinkerTool has to follow
                // right after build_interface_libraries.
                cppDynamicLibraryLinkerTool,
                "feature {",
                "   name: 'symbol_counts'",
                "   flag_set {",
                "       expand_if_all_available: 'symbol_counts_output'",
                "       action: 'c++-link-executable'",
                "       action: 'c++-link-dynamic-library'",
                "       flag_group {",
                "           flag: '-Wl,--print-symbol-counts=%{symbol_counts_output}'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'shared_flag'",
                "   flag_set {",
                "       action: 'c++-link-dynamic-library'",
                "       flag_group {",
                "           flag: '-shared'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'linkstamps'",
                "   flag_set {",
                "       action: 'c++-link-executable'",
                "       action: 'c++-link-dynamic-library'",
                "       expand_if_all_available: 'linkstamp_paths'",
                "       flag_group {",
                "           flag: '%{linkstamp_paths}'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'output_execpath_flags'",
                "   flag_set {",
                "       expand_if_all_available: 'output_execpath'",
                "       action: 'c++-link-dynamic-library'",
                "       flag_group {",
                "           flag: '-o'",
                "           flag: '%{output_execpath}'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'output_execpath_flags_executable'",
                "   flag_set {",
                "      expand_if_all_available: 'output_execpath'",
                "      action: 'c++-link-executable'",
                "      flag_group {",
                "         flag: '-o'",
                "      }",
                "   }",
                "   flag_set {",
                "      expand_if_all_available: 'skip_mostly_static'",
                "      expand_if_all_available: 'output_execpath'",
                "      action: 'c++-link-executable'",
                "      flag_group {",
                "         flag: '/dev/null'",
                "         flag: '-MMD'",
                "         flag: '-MF'",
                "      }",
                "   }",
                "   flag_set {",
                "      expand_if_all_available: 'output_execpath'",
                "      action: 'c++-link-executable'",
                "      flag_group {",
                "         flag: '%{output_execpath}'",
                "      }",
                "   }",
                "}",
                "feature {",
                "   name: 'runtime_library_search_directories',",
                "   flag_set {",
                "       expand_if_all_available: 'runtime_library_search_directories'",
                "       action: 'c++-link-executable'",
                "       action: 'c++-link-dynamic-library'",
                "       action: 'c++-link-static-library'",
                "       action: 'c++-link-alwayslink-static-library'",
                "       action: 'c++-link-pic-static-library'",
                "       action: 'c++-link-alwayslink-pic-static-library'",
                "       flag_group {",
                "           iterate_over: 'runtime_library_search_directories'",
                "           flag_group {",
                // TODO(b/27153401): This should probably be @loader_path on osx.
                ifTrue(
                    supportsEmbeddedRuntimes,
                    "           expand_if_all_available: 'is_cc_test_link_action'",
                    "           flag: ",
                    "             '-Wl,-rpath,$EXEC_ORIGIN/%{runtime_library_search_directories}'",
                    "       }",
                    "       flag_group {",
                    "           expand_if_all_available: 'is_not_cc_test_link_action'"),
                "               flag: '-Wl,-rpath,$ORIGIN/%{runtime_library_search_directories}'",
                "           }",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'library_search_directories'",
                "   flag_set {",
                "       expand_if_all_available: 'library_search_directories'",
                "       action: 'c++-link-executable'",
                "       action: 'c++-link-dynamic-library'",
                "       action: 'c++-link-static-library'",
                "       action: 'c++-link-alwayslink-static-library'",
                "       action: 'c++-link-pic-static-library'",
                "       action: 'c++-link-alwayslink-pic-static-library'",
                "       flag_group {",
                "           iterate_over: 'library_search_directories'",
                "           flag: '-L%{library_search_directories}'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'libraries_to_link'",
                "   flag_set {",
                "       expand_if_all_available: 'libraries_to_link'",
                "       action: 'c++-link-executable'",
                "       action: 'c++-link-dynamic-library'",
                "       action: 'c++-link-static-library'",
                "       action: 'c++-link-alwayslink-static-library'",
                "       action: 'c++-link-pic-static-library'",
                "       action: 'c++-link-alwayslink-pic-static-library'",
                "       flag_group {",
                "           iterate_over: 'libraries_to_link'",
                "           flag_group {",
                "               expand_if_equal: {",
                "                   variable: 'libraries_to_link.type'",
                "                   value: 'object_file_group'",
                "               }",
                "               flag: '-Wl,--start-lib'",
                "           }",
                ifLinux(
                    platform,
                    "       flag_group {",
                    "           expand_if_true: 'libraries_to_link.is_whole_archive'",
                    "           flag: '-Wl,-whole-archive'",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'object_file_group'",
                    "           }",
                    "           iterate_over: 'libraries_to_link.object_files'",
                    "           flag: '%{libraries_to_link.object_files}'",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'object_file'",
                    "           }",
                    "           flag: '%{libraries_to_link.name}'",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'interface_library'",
                    "           }",
                    "           flag: '%{libraries_to_link.name}'",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'static_library'",
                    "           }",
                    "           flag: '%{libraries_to_link.name}'",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'dynamic_library'",
                    "           }",
                    "           flag: '-l%{libraries_to_link.name}'",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'versioned_dynamic_library'",
                    "           }",
                    "           flag: '-l:%{libraries_to_link.name}'",
                    "       }",
                    "       flag_group {",
                    "           expand_if_true: 'libraries_to_link.is_whole_archive'",
                    "           flag: '-Wl,-no-whole-archive'",
                    "       }"),
                ifMac(
                    platform,
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'object_file_group'",
                    "           }",
                    "           iterate_over: 'libraries_to_link.object_files'",
                    "           flag_group {",
                    "               expand_if_false: 'libraries_to_link.is_whole_archive'",
                    "               flag: '%{libraries_to_link.object_files}'",
                    "           }",
                    "           flag_group {",
                    "               expand_if_true: 'libraries_to_link.is_whole_archive'",
                    "               flag: '-Wl,-force_load,%{libraries_to_link.object_files}'",
                    "           }",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'object_file'",
                    "           }",
                    "           flag_group {",
                    "               expand_if_false: 'libraries_to_link.is_whole_archive'",
                    "               flag: '%{libraries_to_link.name}'",
                    "           }",
                    "           flag_group {",
                    "               expand_if_true: 'libraries_to_link.is_whole_archive'",
                    "               flag: '-Wl,-force_load,%{libraries_to_link.name}'",
                    "           }",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'interface_library'",
                    "           }",
                    "           flag_group {",
                    "               expand_if_false: 'libraries_to_link.is_whole_archive'",
                    "               flag: '%{libraries_to_link.name}'",
                    "           }",
                    "           flag_group {",
                    "               expand_if_true: 'libraries_to_link.is_whole_archive'",
                    "               flag: '-Wl,-force_load,%{libraries_to_link.name}'",
                    "           }",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'static_library'",
                    "           }",
                    "           flag_group {",
                    "               expand_if_false: 'libraries_to_link.is_whole_archive'",
                    "               flag: '%{libraries_to_link.name}'",
                    "           }",
                    "           flag_group {",
                    "               expand_if_true: 'libraries_to_link.is_whole_archive'",
                    "               flag: '-Wl,-force_load,%{libraries_to_link.name}'",
                    "           }",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'dynamic_library'",
                    "           }",
                    "           flag: '-l%{libraries_to_link.name}'",
                    "       }",
                    "       flag_group {",
                    "           expand_if_equal: {",
                    "               variable: 'libraries_to_link.type'",
                    "               value: 'versioned_dynamic_library'",
                    "           }",
                    "           flag: '-l:%{libraries_to_link.name}'",
                    "       }"),
                "           flag_group {",
                "               expand_if_equal: {",
                "                   variable: 'libraries_to_link.type'",
                "                   value: 'object_file_group'",
                "               }",
                "               flag: '-Wl,--end-lib'",
                "           }",
                "       }",
                "       flag_group {",
                "           expand_if_true: 'thinlto_param_file'",
                "           flag: '-Wl,@%{thinlto_param_file}'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'force_pic_flags'",
                "   flag_set {",
                "       expand_if_all_available: 'force_pic'",
                "       action: 'c++-link-executable'",
                "       flag_group {",
                "           flag: '-pie'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'legacy_link_flags'",
                "   flag_set {",
                "       expand_if_all_available: 'legacy_link_flags'",
                "       action: 'c++-link-executable'",
                "       action: 'c++-link-dynamic-library'",
                "       flag_group {",
                "           iterate_over: 'legacy_link_flags'",
                "           flag: '%{legacy_link_flags}'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'fission_support'",
                "   flag_set {",
                "       action: 'c++-link-executable'",
                "       action: 'c++-link-dynamic-library'",
                "       action: 'c++-link-interface-dynamic-library'",
                "       flag_group {",
                "           expand_if_all_available: 'is_using_fission'",
                "           flag: '-Wl,--gdb-index'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'strip_debug_symbols'",
                "   flag_set {",
                "       action: 'c++-link-executable'",
                "       action: 'c++-link-dynamic-library'",
                "       action: 'c++-link-interface-dynamic-library'",
                "       flag_group {",
                "           expand_if_all_available: 'strip_debug_symbols'",
                "           flag: '-Wl,-S'",
                "       }",
                "   }",
                "}",
                "feature {",
                "   name: 'linker_param_file'",
                "   flag_set {",
                "       expand_if_all_available: 'linker_param_file'",
                "       action: 'c++-link-executable'",
                "       action: 'c++-link-dynamic-library'",
                "       flag_group {",
                "           flag: '-Wl,@%{linker_param_file}'",
                "       }",
                "   }",
                "   flag_set {",
                "       expand_if_all_available: 'linker_param_file'",
                "       action: 'c++-link-static-library'",
                "       action: 'c++-link-alwayslink-static-library'",
                "       action: 'c++-link-pic-static-library'",
                "       action: 'c++-link-alwayslink-pic-static-library'",
                "       flag_group {",
                "           flag: '@%{linker_param_file}'",
                "       }",
                "   }",
                "}"));
  }

  private static String ifLinux(CppLinkPlatform platform, String... lines) {
    return ifTrue(platform == CppLinkPlatform.LINUX, lines);
  }

  private static String ifMac(CppLinkPlatform platform, String... lines) {
    return ifTrue(platform == CppLinkPlatform.MAC, lines);
  }

  private static String ifTrue(boolean condition, String... lines) {
    if (condition) {
      return Joiner.on("\n").join(lines);
    } else {
      return "";
    }
  }
}
