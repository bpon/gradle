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

package org.gradle.integtests.tooling.r40

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.ProjectConnection

@ToolingApiVersion(">=2.5")
@TargetGradleVersion(">=4.0")
class PluginApplicationBuildProgressCrossVersionSpec extends ToolingApiSpecification {

    def "generates plugin application events for single project build"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << "rootProject.name = 'single'"
        buildFile << """
            apply plugin: 'java'
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def configureRootProject = events.operation("Configure project :")
        def applyBuildGradle = events.operation("Apply script build.gradle to root project 'single'")

        def help = events.operation("Apply plugin org.gradle.help-tasks to root project 'single'")
        def java = events.operation("Apply plugin org.gradle.java to root project 'single'")
        def javaBase = events.operation("Apply plugin org.gradle.api.plugins.JavaBasePlugin to root project 'single'")
        def base = events.operation("Apply plugin org.gradle.api.plugins.BasePlugin to root project 'single'")

        help.parent == configureRootProject
        java.parent == applyBuildGradle
        javaBase.parent == java
        base.parent == javaBase
    }

    def "generates plugin application events for core plugin applied through plugins dsl"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << "rootProject.name = 'single'"
        buildFile << """
            plugins { 
                id 'java'
            }
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def configureRootProject = events.operation("Configure project :")
        def applyBuildGradle = events.operation("Apply script build.gradle to root project 'single'")

        def help = events.operation("Apply plugin org.gradle.help-tasks to root project 'single'")
        def java = events.operation("Apply plugin org.gradle.java to root project 'single'")
        def javaBase = events.operation("Apply plugin org.gradle.api.plugins.JavaBasePlugin to root project 'single'")
        def base = events.operation("Apply plugin org.gradle.api.plugins.BasePlugin to root project 'single'")

        help.parent == configureRootProject
        java.parent == applyBuildGradle
        javaBase.parent == java
        base.parent == javaBase
    }

    def "generates plugin application events for community plugin applied through plugins dsl"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << "rootProject.name = 'single'"
        buildFile << """
            plugins {
                id "org.gradle.hello-world" version "0.2"
            }
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def configureRootProject = events.operation("Configure project :")
        def applyBuildGradle = events.operation("Apply script build.gradle to root project 'single'")

        def help = events.operation("Apply plugin org.gradle.help-tasks to root project 'single'")
        def helloWorld = events.operation("Apply plugin org.gradle.hello-world to root project 'single'")

        help.parent == configureRootProject
        helloWorld.parent == applyBuildGradle
        helloWorld.descriptor.name == "Apply plugin org.gradle.hello-world"
    }

    def "generates plugin application events for plugin applied in settings script"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << """
            rootProject.name = 'single'
            apply plugin: ExamplePlugin
            
            class ExamplePlugin implements Plugin<Object> {
                void apply(Object target) { }
            }
        """
        buildFile << ""

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def applySettings = events.operation("Apply script settings.gradle to settings '${projectDir.name}'")
        def examplePlugin = events.operation("Apply plugin ExamplePlugin to settings 'single'")

        examplePlugin.parent == applySettings
    }

    def "generates plugin application events for plugin applied in init script"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << "rootProject.name = 'single'"
        def initScript = file('init.gradle')
        buildFile << ""
        initScript  << """
            apply plugin: ExamplePlugin
            
            class ExamplePlugin implements Plugin<Object> {
                void apply(Object target) { }
            }
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events)
                .withArguments('--init-script', initScript.toString()).run()
        }

        then:
        events.assertIsABuild()

        def applyInitScript = events.operation("Apply script init.gradle to build")
        def examplePlugin = events.operation("Apply plugin ExamplePlugin to build")

        examplePlugin.parent == applyInitScript
    }

    def "generates plugin application events for project plugin applied in init script to root project"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << "rootProject.name = 'single'"
        def initScript = file('init.gradle')
        buildFile << ""
        initScript  << """
            rootProject { 
                apply plugin: 'java'
            }
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events)
                .withArguments('--init-script', initScript.toString()).run()
        }

        then:
        events.assertIsABuild()

        def rootOperation = events.operations[0]

        def java = events.operation("Apply plugin org.gradle.java to root project 'single'")
        def javaBase = events.operation("Apply plugin org.gradle.api.plugins.JavaBasePlugin to root project 'single'")
        def base = events.operation("Apply plugin org.gradle.api.plugins.BasePlugin to root project 'single'")
        def rootProjectAction = rootOperation.child("Executing 'rootProject {}' action")

        java.parent == rootProjectAction.child("Cross-configure project :")
        javaBase.parent == java
        base.parent == javaBase
    }

    def "generates plugin application events for project plugin applied in init script to all projects"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << "rootProject.name = 'single'"
        def initScript = file('init.gradle')
        buildFile << ""
        initScript  << """
            allprojects { 
                apply plugin: 'java'
            }
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events)
                .withArguments('--init-script', initScript.toString()).run()
        }

        then:
        events.assertIsABuild()

        def rootOperation = events.operations[0]

        def java = events.operation("Apply plugin org.gradle.java to root project 'single'")
        def javaBase = events.operation("Apply plugin org.gradle.api.plugins.JavaBasePlugin to root project 'single'")
        def base = events.operation("Apply plugin org.gradle.api.plugins.BasePlugin to root project 'single'")
        def rootProjectAction = rootOperation.child("Executing 'rootProject {}' action")

        java.parent == rootProjectAction.child("Cross-configure project :").child("Executing 'allprojects {}' action").child("Cross-configure project :")
        javaBase.parent == java
        base.parent == javaBase
    }

    def "generates plugin application events for multi-project build"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def configureRoot = events.operation("Configure project :")
        configureRoot.child("Apply plugin org.gradle.help-tasks to root project 'multi'")

        def configureA = events.operation("Configure project :a")
        configureA.child("Apply plugin org.gradle.help-tasks to project ':a'")

        def configureB = events.operation("Configure project :b")
        configureB.child("Apply plugin org.gradle.help-tasks to project ':b'")
    }

    def "generates plugin application events when configuration fails"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        file("a/build.gradle") << """
            throw new RuntimeException("broken")
"""

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        def e = thrown(BuildException)
        e.cause.message =~ /A problem occurred evaluating project ':a'/

        events.assertIsABuild()

        def configureBuild = events.operation("Configure build")
        configureBuild.failed

        def configureRoot = events.operation("Configure project :")
        configureRoot.child("Apply plugin org.gradle.help-tasks to root project 'multi'")

        events.operation("Configure project :a").failed
    }

    def "generates plugin application events where project configuration is allprojects closure"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        buildFile << """
            allprojects { apply plugin: 'java' }
        """


        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def configureBuild =  events.operation("Configure build")

        def configureRoot = configureBuild.child("Configure project :")
        configureRoot.child("Apply plugin org.gradle.help-tasks to root project 'multi'")

        def applyBuildGradle = events.operation("Apply script build.gradle to root project 'multi'")
        applyBuildGradle.children("Apply plugin org.gradle.java to root project 'multi'").empty

        def configureA = configureBuild.child("Configure project :a")
        configureA.child("Apply plugin org.gradle.help-tasks to project ':a'")
        configureA.children("Apply plugin'org.gradle.java' to project ':a'").empty

        def configureB = configureBuild.child("Configure project :b")
        configureB.child("Apply plugin org.gradle.help-tasks to project ':b'")
        configureB.children("Apply plugin'org.gradle.java' to project ':b'").empty

        applyBuildGradle.child("Executing 'allprojects {}' action").child("Cross-configure project :").child("Apply plugin org.gradle.java to root project 'multi'")
        applyBuildGradle.child("Executing 'allprojects {}' action").child("Cross-configure project :a").child("Apply plugin org.gradle.java to project ':a'")
        applyBuildGradle.child("Executing 'allprojects {}' action").child("Cross-configure project :b").child("Apply plugin org.gradle.java to project ':b'")
    }

    def "generates plugin application events where project configuration is subprojects closure"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        buildFile << """
            subprojects { apply plugin: 'java' }
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def applyBuildGradle = events.operation("Apply script build.gradle to root project 'multi'")

        applyBuildGradle.children.size() == 1
        applyBuildGradle.child("Executing 'subprojects {}' action").child("Cross-configure project :a").child("Apply plugin org.gradle.java to project ':a'")
        applyBuildGradle.child("Executing 'subprojects {}' action").child("Cross-configure project :b").child("Apply plugin org.gradle.java to project ':b'")
    }

    def "generates plugin application events where project configuration is project closure"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        buildFile << """
            project(':a') { apply plugin: 'java' }
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def applyBuildGradle = events.operation("Apply script build.gradle to root project 'multi'")

        applyBuildGradle.children.size() == 1
        applyBuildGradle.child("Cross-configure project :a").child("Apply plugin org.gradle.java to project ':a'")
    }

    def "generates plugin application events where project configuration is project configuration action"() {
        given:
        def events = ProgressEvents.create()
        settingsFile << """
            rootProject.name = 'multi'
            include 'a', 'b'
        """
        buildFile << """
            project(':b', new Action<Project>() { void execute(Project project) { project.apply plugin: 'java' } })
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def applyBuildGradle = events.operation("Apply script build.gradle to root project 'multi'")

        applyBuildGradle.children.size() == 1
        applyBuildGradle.child("Cross-configure project :b").child("Apply plugin org.gradle.java to project ':b'")
    }

    def "generates plugin application events for buildSrc"() {
        given:
        def events = ProgressEvents.create()
        buildSrc()
        buildFile << """
            apply plugin: 'java'
        """

        when:
        withConnection {
            ProjectConnection connection -> connection.newBuild().addProgressListener(events).run()
        }

        then:
        events.assertIsABuild()

        def buildSrc = events.operation("Build buildSrc")
        def groovyPlugin = buildSrc.child("Apply plugin org.gradle.api.plugins.GroovyPlugin to project ':buildSrc'")
        def configureBuildSrcBuild = buildSrc.child({ it.startsWith("Configure build") })

        def configureBuildSrcRoot = configureBuildSrcBuild.child("Configure project :buildSrc")
        configureBuildSrcRoot.child("Apply plugin org.gradle.help-tasks to project ':buildSrc'")
        configureBuildSrcRoot.children("Apply plugin org.gradle.java to project ':buildSrc'").empty

        def applyBuildSrcBuildGradle = events.operation("Apply script build.gradle to project ':buildSrc'")
        applyBuildSrcBuildGradle.children("Apply plugin org.gradle.java to project ':buildSrc'").empty

        def configureBuildSrcA = configureBuildSrcBuild.child("Configure project :buildSrc:a")
        configureBuildSrcA.child("Apply plugin org.gradle.help-tasks to project ':buildSrc:a'")
        configureBuildSrcA.children("Apply plugin org.gradle.java to project ':buildSrc:a'").empty

        def configureBuildSrcB = configureBuildSrcBuild.child("Configure project :buildSrc:b")
        configureBuildSrcB.child("Apply plugin org.gradle.help-tasks to project ':buildSrc:b'")
        configureBuildSrcB.children("Apply plugin org.gradle.java to project ':buildSrc:b'").empty

        groovyPlugin.child("Apply plugin org.gradle.api.plugins.JavaPlugin to project ':buildSrc'")
        applyBuildSrcBuildGradle.child("Executing 'allprojects {}' action").child("Cross-configure project :buildSrc").children.empty //Java plugin is applied by groovy plugin, so it is not applied again
        applyBuildSrcBuildGradle.child("Executing 'allprojects {}' action").child("Cross-configure project :buildSrc:a").child("Apply plugin org.gradle.java to project ':buildSrc:a'")
        applyBuildSrcBuildGradle.child("Executing 'allprojects {}' action").child("Cross-configure project :buildSrc:b").child("Apply plugin org.gradle.java to project ':buildSrc:b'")
    }

    private buildSrc() {
        file("buildSrc/settings.gradle") << "include 'a', 'b'"
        file("buildSrc/build.gradle") << """
            allprojects {   
                apply plugin: 'java'
            }
            dependencies {
                compile project(':a')
                compile project(':b')
            }
        """
    }
}
