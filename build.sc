// SPDX-License-Identifier: Apache-2.0

import mill._
import scalalib._
import publish._
import mill.util.Jvm

import $ivy.`io.chris-kipp::mill-ci-release::0.1.9`
import io.kipp.mill.ci.release.{CiReleaseModule, SonatypeHost}

case class Platform(os: String, arch: String) {
  override def toString: String = s"$os-$arch"
}
object Platform {
  // Needed to use Platform's in T[_] results
  import upickle.default.{ReadWriter, macroRW}
  implicit val rw: ReadWriter[Platform] = macroRW
}

object `llvm-firtool` extends JavaModule with CiReleaseModule {

  // This cannot be cached in case user changes the environment variable
  override def publishVersion = T.command {
    // We can't use the default VcsVersion because we can have multiple tags on the same commit
    val versionOpt = sys.env.get("LLVM_FIRTOOL_VERSION")
    require(versionOpt.exists(_.nonEmpty), "Version must be set in environment variable LLVM_FIRTOOL_VERSION")
    println(versionOpt)
    versionOpt.get.stripPrefix("v") // Tags have a leading v
  }

  def FNDDSSpecVersion = "1.0.0"
  def groupId = "org.chipsalliance"
  // artifactId is the the name of this object
  def artId = "llvm-firtool"
  def binName = "firtool"
  def releaseUrl = T {
    val firtoolVersion = publishVersion().split('-').head
    s"https://github.com/llvm/circt/releases/download/firtool-${firtoolVersion}"
  }

  // org.chipsalliance is published at s01.sonatype
  override def sonatypeHost = Some(SonatypeHost.s01)

  val platforms = Seq[Platform](
    Platform("macos", "x64"),
    Platform("linux", "x64"),
    Platform("windows", "x64")
  )

  def pomSettings = PomSettings(
    description = "Package of native firtool binary",
    organization = "org.chipsalliance",
    url = "https://chipsalliance.org",
    licenses = Seq(License.`Apache-2.0`),
    versionControl = VersionControl.github("chipsalliance", "llvm-firtool"),
    developers = Seq(
      Developer("jackkoenig", "Jack Koenig", "https://github.com/jackkoenig")
    )
  )

  private def getBaseDir(dir: os.Path): os.Path = dir / groupId / artId

  // Downloaded tarball for each platform
  def tarballs = T {
    platforms.map { platform =>
      val tarballName = if (platform.os == "windows") {
        s"firrtl-bin-$platform.zip"
      } else {
        s"firrtl-bin-$platform.tar.gz"
      }
      val archiveUrl = s"${releaseUrl()}/$tarballName"
      val file = T.dest / tarballName
      os.write(file, requests.get.stream(archiveUrl))
      platform -> PathRef(file)
    }
  }

  def extractedDirs = T {
    val tarballLookup = tarballs().toMap
    platforms.map { platform =>
      val tarball = tarballLookup(platform)
      val dir = T.dest / platform.toString
      os.makeDir.all(dir)
      // Windows uses .zip
      if (platform.os == "windows") {
        os.proc("unzip", tarball.path)
          .call(cwd = dir)
      } else {
        os.proc("tar", "zxf", tarball.path)
          .call(cwd = dir)
      }
      val downloadedDir = os.list(dir).head
      val baseDir = getBaseDir(dir)
      os.makeDir.all(baseDir)

      // Rename the directory to the FNNDS specificed path
      val artDir = baseDir / platform.toString
      os.move(downloadedDir, artDir)

      // If on windows, rename firtool.exe to firtool
      if (platform.os == "windows") {
        os.walk(artDir).foreach { path =>
          if (path.baseName == "firtool" && path.ext == "exe") {
            // OS lib doesn't seem to have a way to get the directory
            val parent = os.Path(path.toIO.getParentFile)
            os.move(path, parent / path.baseName)
          }
        }
      }

      platform -> PathRef(dir)
    }
  }

  // Directories that will be turned into platform-specific classifier jars
  def classifierDirs = T {
    extractedDirs().map { case (platform, dir) =>
      os.copy.into(dir.path, T.dest)
      // We added a platform directory above in extractedDirs, remove it to get actual root
      val rootDir = T.dest / platform.toString

      platform -> PathRef(rootDir)
    }
  }

  // Classifier jars will be included as extras so that platform-specific jars can be fetched
  def classifierJars = T {
    classifierDirs().map { case (platform, dir) =>
      val jarPath = T.dest / s"$platform.jar"
      Jvm.createJar(
        jarPath,
        Agg(dir.path, fnddsMetadata().path),
        mill.api.JarManifest.MillDefault,
        (_, _) => true
      )
      platform -> PathRef(jarPath)
    }
  }

  def extraPublish = T {
    classifierJars().map { case (platform, jar) =>
      PublishInfo(
        jar,
        classifier = Some(platform.toString),
        ext = "jar",
        ivyConfig = "compile" // TODO is this right?
      )
    }
  }

  def fnddsMetadata = T {
    // Then get the baseDir from there
    val baseDir = getBaseDir(T.dest)
    os.makeDir.all(baseDir)
    os.write(baseDir / "FNDDS.version", FNDDSSpecVersion)
    os.write(baseDir / "project.version", publishVersion())
    PathRef(T.dest)
  }

  def localClasspath = T {
    super.localClasspath() ++ extractedDirs().map { case (_, dir) => dir } ++ Seq(fnddsMetadata())
  }
}

