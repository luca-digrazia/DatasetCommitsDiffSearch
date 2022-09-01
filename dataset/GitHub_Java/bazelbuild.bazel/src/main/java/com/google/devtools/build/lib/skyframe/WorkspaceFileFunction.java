// Copyright 2014 Google Inc. All rights reserved.
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

import static com.google.devtools.build.lib.syntax.Environment.NONE;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.cmdline.LabelValidator;
import com.google.devtools.build.lib.events.Event;
import com.google.devtools.build.lib.events.StoredEventHandler;
import com.google.devtools.build.lib.packages.ExternalPackage.Binding;
import com.google.devtools.build.lib.packages.ExternalPackage.ExternalPackageBuilder;
import com.google.devtools.build.lib.packages.ExternalPackage.ExternalPackageBuilder.NoSuchBindingException;
import com.google.devtools.build.lib.packages.Package.NameConflictException;
import com.google.devtools.build.lib.packages.PackageFactory;
import com.google.devtools.build.lib.packages.RuleClass;
import com.google.devtools.build.lib.packages.RuleFactory;
import com.google.devtools.build.lib.packages.Type;
import com.google.devtools.build.lib.packages.Type.ConversionException;
import com.google.devtools.build.lib.syntax.AbstractFunction;
import com.google.devtools.build.lib.syntax.BuildFileAST;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.FuncallExpression;
import com.google.devtools.build.lib.syntax.Function;
import com.google.devtools.build.lib.syntax.Label;
import com.google.devtools.build.lib.syntax.Label.SyntaxException;
import com.google.devtools.build.lib.syntax.MixedModeFunction;
import com.google.devtools.build.lib.syntax.ParserInputSource;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.vfs.RootedPath;
import com.google.devtools.build.skyframe.SkyFunction;
import com.google.devtools.build.skyframe.SkyFunctionException;
import com.google.devtools.build.skyframe.SkyFunctionException.Transience;
import com.google.devtools.build.skyframe.SkyKey;
import com.google.devtools.build.skyframe.SkyValue;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A SkyFunction to parse WORKSPACE files.
 */
public class WorkspaceFileFunction implements SkyFunction {

  private static final String BIND = "bind";

  private final PackageFactory packageFactory;

  WorkspaceFileFunction(PackageFactory packageFactory) {
    this.packageFactory = packageFactory;
  }

  @Override
  public SkyValue compute(SkyKey skyKey, Environment env) throws WorkspaceFileFunctionException,
      InterruptedException {
    RootedPath workspaceRoot = (RootedPath) skyKey.argument();
    // Explicitly make skyframe load this file.
    if (env.getValue(FileValue.key(workspaceRoot)) == null) {
      return null;
    }
    Path workspaceFilePath = workspaceRoot.getRoot().getRelative(workspaceRoot.getRelativePath());
    WorkspaceNameHolder holder = new WorkspaceNameHolder();
    ExternalPackageBuilder builder = new ExternalPackageBuilder(workspaceFilePath);
    StoredEventHandler localReporter = new StoredEventHandler();
    BuildFileAST buildFileAST;
    ParserInputSource inputSource = null;

    try {
      inputSource = ParserInputSource.create(workspaceFilePath);
    } catch (IOException e) {
      throw new WorkspaceFileFunctionException(e, Transience.TRANSIENT);
    }
    buildFileAST = BuildFileAST.parseBuildFile(inputSource, localReporter, null, false);
    if (buildFileAST.containsErrors()) {
      localReporter.handle(Event.error("WORKSPACE file could not be parsed"));
    } else {
      try {
        if (!evaluateWorkspaceFile(buildFileAST, holder, builder)) {
          localReporter.handle(
              Event.error("Error evaluating WORKSPACE file " + workspaceFilePath));
        }
      } catch (EvalException e) {
        throw new WorkspaceFileFunctionException(e);
      }
    }

    builder.addEvents(localReporter.getEvents());
    if (localReporter.hasErrors()) {
      builder.setContainsErrors();
    }
    return new WorkspaceFileValue(holder.workspaceName, builder.build());
  }

  @Override
  public String extractTag(SkyKey skyKey) {
    return null;
  }

  private static Function newWorkspaceNameFunction(final WorkspaceNameHolder holder) {
    List<String> params = ImmutableList.of("name");
    return new MixedModeFunction("workspace", params, 1, true) {
      @Override
      public Object call(Object[] namedArgs, FuncallExpression ast) throws EvalException,
          ConversionException, InterruptedException {
        String name = Type.STRING.convert(namedArgs[0], "'name' argument");
        String errorMessage = LabelValidator.validateTargetName(name);
        if (errorMessage != null) {
          throw new EvalException(ast.getLocation(), errorMessage);
        }
        holder.workspaceName = name;
        return NONE;
      }
    };
  }

  private static Function newBindFunction(final ExternalPackageBuilder builder) {
    List<String> params = ImmutableList.of("name", "actual");
    return new MixedModeFunction(BIND, params, 2, true) {
      @Override
      public Object call(Object[] namedArgs, FuncallExpression ast)
              throws EvalException, ConversionException {
        String name = Type.STRING.convert(namedArgs[0], "'name' argument");
        String actual = Type.STRING.convert(namedArgs[1], "'actual' argument");

        Label nameLabel = null;
        try {
          nameLabel = Label.parseAbsolute("//external:" + name);
          builder.addBinding(
              nameLabel, new Binding(Label.parseRepositoryLabel(actual), ast.getLocation()));
        } catch (SyntaxException e) {
          throw new EvalException(ast.getLocation(), e.getMessage());
        }

        return NONE;
      }
    };
  }

  /**
   * Returns a function-value implementing the build rule "ruleClass" (e.g. cc_library) in the
   * specified package context.
   */
  private static Function newRuleFunction(final RuleFactory ruleFactory,
      final ExternalPackageBuilder builder, final String ruleClassName) {
    return new AbstractFunction(ruleClassName) {
      @Override
      public Object call(List<Object> args, Map<String, Object> kwargs, FuncallExpression ast,
          com.google.devtools.build.lib.syntax.Environment env)
          throws EvalException {
        if (!args.isEmpty()) {
          throw new EvalException(ast.getLocation(),
              "build rules do not accept positional parameters");
        }

        try {
          RuleClass ruleClass = ruleFactory.getRuleClass(ruleClassName);
          builder.createAndAddRepositoryRule(ruleClass, kwargs, ast);
        } catch (RuleFactory.InvalidRuleException | NameConflictException | SyntaxException e) {
          throw new EvalException(ast.getLocation(), e.getMessage());
        }
        return NONE;
      }
    };
  }

  public boolean evaluateWorkspaceFile(BuildFileAST buildFileAST, WorkspaceNameHolder holder,
      ExternalPackageBuilder builder)
          throws InterruptedException, EvalException, WorkspaceFileFunctionException {
    // Environment is defined in SkyFunction and the syntax package.
    com.google.devtools.build.lib.syntax.Environment workspaceEnv =
        new com.google.devtools.build.lib.syntax.Environment();

    RuleFactory ruleFactory = new RuleFactory(packageFactory.getRuleClassProvider());
    for (String ruleClass : ruleFactory.getRuleClassNames()) {
      Function ruleFunction = newRuleFunction(ruleFactory, builder, ruleClass);
      workspaceEnv.update(ruleClass, ruleFunction);
    }

    workspaceEnv.update(BIND, newBindFunction(builder));
    workspaceEnv.update("workspace", newWorkspaceNameFunction(holder));

    StoredEventHandler eventHandler = new StoredEventHandler();
    if (!buildFileAST.exec(workspaceEnv, eventHandler)) {
      return false;
    }
    try {
      builder.resolveBindTargets(packageFactory.getRuleClass(BIND));
    } catch (NoSuchBindingException e) {
      throw new WorkspaceFileFunctionException(e);
    }
    return true;
  }

  private static final class WorkspaceNameHolder {
    String workspaceName;
  }

  private static final class WorkspaceFileFunctionException extends SkyFunctionException {
    public WorkspaceFileFunctionException(IOException e, Transience transience) {
      super(e, transience);
    }

    public WorkspaceFileFunctionException(NoSuchBindingException e) {
      super(e, Transience.PERSISTENT);
    }

    public WorkspaceFileFunctionException(EvalException e) {
      super(e, Transience.PERSISTENT);
    }
  }
}
