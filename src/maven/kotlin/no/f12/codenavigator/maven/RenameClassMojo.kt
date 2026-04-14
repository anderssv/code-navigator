package no.f12.codenavigator.maven

import org.apache.maven.plugins.annotations.Mojo

@Mojo(name = "rename-class")
class RenameClassMojo : MoveClassMojo()
