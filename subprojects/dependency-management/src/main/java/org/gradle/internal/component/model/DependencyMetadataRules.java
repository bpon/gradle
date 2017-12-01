/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.internal.component.model;


import org.gradle.api.Action;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DependencyConstraintsMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependenciesMetadataAdapter;
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintsMetadataAdapter;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of rules provided by the build script author (as {@link Action< DirectDependenciesMetadata >}) that
 * are applied on the dependencies defined in variant/configuration metadata. The rules are applied
 * in the {@link #execute(List)} method when the dependencies of a variant are needed during dependency resolution.
 */
public class DependencyMetadataRules {
    private static final Spec<ModuleDependencyMetadata> DEPENDENCY_FILTER = new Spec<ModuleDependencyMetadata>() {
        @Override
        public boolean isSatisfiedBy(ModuleDependencyMetadata dep) {
            return !dep.isPending();
        }
    };
    private static final Spec<ModuleDependencyMetadata> DEPENDENCY_CONSTRAINT_FILTER = new Spec<ModuleDependencyMetadata>() {
        @Override
        public boolean isSatisfiedBy(ModuleDependencyMetadata dep) {
            return dep.isPending();
        }
    };

    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser;
    private final List<Action<DirectDependenciesMetadata>> dependencyActions = new ArrayList<Action<DirectDependenciesMetadata>>();
    private final List<Action<DependencyConstraintsMetadata>> dependencyConstraintActions = new ArrayList<Action<DependencyConstraintsMetadata>>();

    public DependencyMetadataRules(Instantiator instantiator,
                                   NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser,
                                   NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser) {
        this.instantiator = instantiator;
        this.dependencyNotationParser = dependencyNotationParser;
        this.dependencyConstraintNotationParser = dependencyConstraintNotationParser;
    }

    public void addDependencyAction(Action<DirectDependenciesMetadata> action) {
        dependencyActions.add(action);
    }

    public void addDependencyConstraintAction(Action<DependencyConstraintsMetadata> action) {
        dependencyConstraintActions.add(action);
    }

    public <T extends ModuleDependencyMetadata> List<T> execute(List<T> dependencies) {
        List<T> calculatedDependencies = new ArrayList<T>();
        calculatedDependencies.addAll(executeDependencyRules(dependencies));
        calculatedDependencies.addAll(executeDependencyConstraintRules(dependencies));
        return calculatedDependencies;
    }

    private <T extends ModuleDependencyMetadata> List<T> executeDependencyRules(List<T> dependencies) {
        List<T> calculatedDependencies = new ArrayList<T>(CollectionUtils.filter(dependencies, DEPENDENCY_FILTER));
        for (Action<DirectDependenciesMetadata> dependenciesMetadataAction : dependencyActions) {
            dependenciesMetadataAction.execute(instantiator.newInstance(
                DirectDependenciesMetadataAdapter.class, calculatedDependencies, instantiator, dependencyNotationParser));
        }
        return calculatedDependencies;
    }

    private <T extends ModuleDependencyMetadata> List<T> executeDependencyConstraintRules(List<T> dependencies) {
        List<T> calculatedDependencies = new ArrayList<T>(CollectionUtils.filter(dependencies, DEPENDENCY_CONSTRAINT_FILTER));
        for (Action<DependencyConstraintsMetadata> dependencyConstraintsMetadataAction : dependencyConstraintActions) {
            dependencyConstraintsMetadataAction.execute(instantiator.newInstance(
                DependencyConstraintsMetadataAdapter.class, calculatedDependencies, instantiator, dependencyConstraintNotationParser));
        }
        return calculatedDependencies;
    }
}
