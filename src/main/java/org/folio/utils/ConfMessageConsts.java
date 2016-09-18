package org.folio.utils;

import org.folio.rest.tools.messages.MessageEnum;


public enum ConfMessageConsts implements MessageEnum {

  UploadFileMissing("30001");
  
  private String code;
  
  private ConfMessageConsts(String code){
    this.code = code;
  }
  
  public String getCode(){
    return code;
  }
  
}

