/*
 * Copyright 2018 the original author or authors.
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

package org.gosulang.gradle.tasks.compile.incremental.recomp;

import org.gosulang.gradle.tasks.compile.incremental.classpath.ClasspathSnapshotData;
import org.gosulang.gradle.tasks.compile.incremental.classpath.ClasspathSnapshotDataSerializer;
import org.gradle.internal.serialize.AbstractSerializer;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.File;

public class PreviousCompilationData {
    private final File destinationDir;
    private final ClasspathSnapshotData classpathSnapshot;

    private static <T> T checkNotNull(T reference, Object errorMessage) {
        if (reference == null) {
            throw new NullPointerException(String.valueOf(errorMessage));
        } else {
            return reference;
        }
    }

    public PreviousCompilationData(File destinationDir, ClasspathSnapshotData classpathSnapshot) {
        this.destinationDir = checkNotNull(destinationDir, "destinationDir");
        this.classpathSnapshot = checkNotNull(classpathSnapshot, "classpathSnapshot");
    }

    public File getDestinationDir() {
        return destinationDir;
    }

    public ClasspathSnapshotData getClasspathSnapshot() {
        return classpathSnapshot;
    }

    public static class Serializer extends AbstractSerializer<PreviousCompilationData> {
        private final ClasspathSnapshotDataSerializer classpathSnapshotDataSerializer;

        public Serializer() {
            classpathSnapshotDataSerializer = new ClasspathSnapshotDataSerializer();
        }

        @Override
        public PreviousCompilationData read(Decoder decoder) throws Exception {
            File destinationDir = BaseSerializerFactory.FILE_SERIALIZER.read(decoder);
            ClasspathSnapshotData classpathSnapshot = classpathSnapshotDataSerializer.read(decoder);
            return new PreviousCompilationData(destinationDir, classpathSnapshot);
        }

        @Override
        public void write(Encoder encoder, PreviousCompilationData value) throws Exception {
            BaseSerializerFactory.FILE_SERIALIZER.write(encoder, value.destinationDir);
            classpathSnapshotDataSerializer.write(encoder, value.classpathSnapshot);
        }
    }
}
