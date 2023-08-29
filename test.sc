#!/usr/bin/env -S scala-cli shebang --scala-version 2.13
//> using dep "com.lihaoyi::os-lib:0.9.1"

// Useful constants
val FNDDSVersion = "1.0.0"

def errOut(): Unit = {
  System.err.println(s"$scriptPath <platform>")
  sys.exit(1)
}

if (args.size != 1) {
  System.err.println("Invalid number of arguments")
  errOut()
}
val platform = args(0)

// Get version
val getVersion = os.proc("./mill", "show", "llvm-firtool.publishVersion").call(cwd = os.pwd)
val publishVersion = getVersion.out.trim().stripPrefix("\"").stripSuffix("\"")
val firtoolVersion = publishVersion.split('-').head // Remove any -suffix

// Do publish local
val repo = os.pwd / "test-repo"
os.proc("./mill", "llvm-firtool.publishM2Local", repo).call(cwd = os.pwd)

val publishDir = repo / "org" / "chipsalliance" / "llvm-firtool" / publishVersion

// Check the contents of a jar for
//  - FNDDS.version
//  - project.version
//  - firtool binary that runs and gives correct version
def checkContents(jar: os.Path): Unit = {
  val root = os.temp.dir(dir = os.pwd, prefix = "firtool-test")

  os.proc("unzip", classifierZip).call(cwd = root)

  val baseDir = root / "org.chipsalliance" / "llvm-firtool"
  // Check FNDDS.version
  val fndds = baseDir / "FNDDS.version"
  assert(os.read(fndds) == FNDDSVersion)

  // Check project.version
  val projVer = baseDir / "project.version"
  assert(os.read(projVer) == publishVersion)

  // Check executable works
  val exe = baseDir / s"$platform-x64" / "bin" / "firtool"
  exe.toIO.setExecutable(true) // os-lib only does POSIX permissions, this supports Windows
  val stdoutLines = os.proc(exe, "--version").call(cwd = os.pwd).out.lines()
  val GetVersion = """CIRCT firtool-(\S*)""".r
  val exeVersion = stdoutLines.collectFirst { case GetVersion(v) => v }.get
  assert(exeVersion == firtoolVersion)
}

// Check classifier-specific jar
val classifierZip = publishDir / s"llvm-firtool-$publishVersion-$platform-x64.jar"

checkContents(classifierZip)

val fullZip = publishDir / s"llvm-firtool-$publishVersion.jar"

checkContents(fullZip)
