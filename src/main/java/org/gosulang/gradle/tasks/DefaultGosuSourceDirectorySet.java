package org.gosulang.gradle.tasks;

import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.internal.file.DefaultSourceDirectorySet;
import org.gradle.api.internal.tasks.TaskDependencyFactory;

import javax.inject.Inject;

public abstract class DefaultGosuSourceDirectorySet extends DefaultSourceDirectorySet implements GosuSourceDirectorySet {

    @Inject
        public DefaultGosuSourceDirectorySet(SourceDirectorySet sourceDirectorySet, TaskDependencyFactory taskDependencyFactory) {
        super(sourceDirectorySet, taskDependencyFactory);
    }
}
