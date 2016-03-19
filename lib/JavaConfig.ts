export interface MavenDependency {
    groupId: string;
    artifactId: string;
    version: string;
}

export interface JavaConfig {
    sourcePath?: string[];
    classPath?: string[];
    classPathFile?: string;
    dependencies?: MavenDependency[];
    outputDirectory?: string;
}