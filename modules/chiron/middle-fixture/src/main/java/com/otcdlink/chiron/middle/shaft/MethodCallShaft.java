package com.otcdlink.chiron.middle.shaft;

import com.otcdlink.chiron.command.Command;

public interface MethodCallShaft< DUTY > {

  /**
   * Blocks until all {@link Command}s reached the end of the Shaft.
   * Then it performs assertions in caller's thread.
   */
  void submit(
      final MethodCaller< DUTY > methodCaller,
      final MethodCallVerifier methodCallVerifier
  ) throws Exception ;

}
