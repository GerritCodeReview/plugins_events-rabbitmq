package com.googlesource.gerrit.plugins.rabbitmq.config.section;

import com.googlesource.gerrit.plugins.rabbitmq.annotation.Default;

public class General implements Section {

  @Default("true")
  public Boolean publishAllGerritEvents;

  @Default("false")
  public Boolean enableBrokerApi;
}
