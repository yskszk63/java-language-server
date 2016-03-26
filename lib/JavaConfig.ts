export interface MavenDependency {
    groupId: string;
    artifactId: string;
    version: string;
}

export interface JavaConfig {
    /**
     * Parent directory of javaconfig.json
     */
    rootPath?: string;
    sourcePath?: string[];
    classPath?: string[];
    classPathFile?: string;
    outputDirectory?: string;
}