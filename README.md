osmLib
======

gradle config : gradle.properties
====================================
ttbox.repo.url=file://${user.home}/project/maven-repo
ttboxSignKeystore=${user.home}/.keystore
ttboxSignStorepass=
ttboxSignKeypass=

gradle config : System
====================================
export GRADLE_OPTS="-Dorg.gradle.daemon=true"


gradle clean assemble publishToMavenLocal


Config Ubuntu 13.10
========================
ia32-libs was delete so install :
sudo apt-get install libc6:i386 libgcc1:i386 gcc-4.6-base:i386 libstdc++5:i386 libstdc++6:i386
