package org.gosulang.gradle.tasks.compile;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.compile.CompileOptions;
import org.gradle.api.tasks.compile.DebugOptions;
import org.gradle.api.tasks.compile.ForkOptions;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MinimalGosuCompileOptions implements Serializable {
    private List<File> _sourcepath;
    private List<String> _compilerArgs;
    private String _encoding;
    private String _extensionDirs;
    private ForkOptions _forkOptions;
    private DebugOptions _debugOptions;
    private boolean _debug;
    private boolean _deprecation;
    private boolean _failOnError;
    private boolean _listFiles;
    private boolean _verbose;
    private boolean _warnings;

    public MinimalGosuCompileOptions(final CompileOptions compileOptions) {
        FileCollection sourcepath = compileOptions.getSourcepath();
        _sourcepath = sourcepath == null ? null : Collections.unmodifiableList(new ArrayList<>(sourcepath.getFiles()));
        _compilerArgs = new ArrayList<>(compileOptions.getCompilerArgs());
        _encoding = compileOptions.getEncoding();
        _extensionDirs = compileOptions.getExtensionDirs();
        _forkOptions = compileOptions.getForkOptions();
        _debugOptions = compileOptions.getDebugOptions();
        _debug = compileOptions.isDebug();
        _deprecation = compileOptions.isDeprecation();
        _failOnError = compileOptions.isFailOnError();
        _listFiles = compileOptions.isListFiles();
        _verbose = compileOptions.isVerbose();
        _warnings = compileOptions.isWarnings();
    }

    public List<File> getSourcepath() {
        return _sourcepath;
    }

    public void setSourcepath(List<File> sourcepath) {
        _sourcepath = sourcepath;
    }

    public List<String> getCompilerArgs() {
        return _compilerArgs;
    }

    public void setCompilerArgs(List<String> compilerArgs) {
        _compilerArgs = compilerArgs;
    }

    public String getEncoding() {
        return _encoding;
    }

    public void setEncoding(String encoding) {
        _encoding = encoding;
    }

    public String getExtensionDirs() {
        return _extensionDirs;
    }

    public void setExtensionDirs(String extensionDirs) {
        _extensionDirs = extensionDirs;
    }

    public ForkOptions getForkOptions() {
        return _forkOptions;
    }

    public void setForkOptions(ForkOptions forkOptions) {
        _forkOptions = forkOptions;
    }

    public DebugOptions getDebugOptions() {
        return _debugOptions;
    }

    public void setDebugOptions(DebugOptions debugOptions) {
        _debugOptions = debugOptions;
    }

    public boolean isDebug() {
        return _debug;
    }

    public void setDebug(boolean debug) {
        _debug = debug;
    }

    public boolean isDeprecation() {
        return _deprecation;
    }

    public void setDeprecation(boolean deprecation) {
        _deprecation = deprecation;
    }


    public boolean isFailOnError() {
        return _failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        _failOnError = failOnError;
    }

    public boolean isListFiles() {
        return _listFiles;
    }

    public void setListFiles(boolean listFiles) {
        _listFiles = listFiles;
    }

    public boolean isVerbose() {
        return _verbose;
    }

    public void setVerbose(boolean verbose) {
        _verbose = verbose;
    }

    public boolean isWarnings() {
        return _warnings;
    }

    public void setWarnings(boolean warnings) {
        _warnings = warnings;
    }

}
