# llvm-firtool

Repackaging of firtool releases as Maven artifacts.

The typical workflow is to create a git tag matching the firtool version (with a leading 'v'), eg.
```bash
git tag v1.52.0
git push --tags
```

The push to the tag will trigger CI to publish a release to Maven.

These tags can also be created in the Github UI.

Manual releases can be created as follows:
```bash
# Set secrets
export PGP_PASSPHRASE==...
export PGP_SECRET==...
export SONATYPE_PASSWORD=...
export SONATYPE_USERNAME=...

# Set version
export LLVM_FIRTOOL_VERSION=<firtool version>

# Publish
# Note the -i, important to use interactive so that mill inherits environment variables
./mill -i io.kipp.mill.ci.release.ReleaseModule/publishAll
```
