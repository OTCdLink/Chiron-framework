package io.github.otcdlink.chiron.ssh.synchronizer;

import com.google.common.collect.ImmutableBiMap;

public enum FileKind {
  COMPILED,
  PACKAGED,

  /**
   * Used by {@code com.otcdlink.piston.driver.remotelauncher.RemoteLauncher} to pass additional
   * files.
   */
  OTHER,
  ;

  public static ImmutableBiMap< FileKind, String > shortDirectoryNameMap() {
    final ImmutableBiMap.Builder< FileKind, String > builder = ImmutableBiMap.builder() ;
    for( final FileKind fileKind : values() ) {
      builder.put( fileKind, fileKind.name().toLowerCase() ) ;
    }
    return builder.build() ;
  }
}
