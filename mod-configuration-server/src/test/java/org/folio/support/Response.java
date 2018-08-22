package org.folio.support;

public class Response {
  private final Integer statusCode;
  private final String body;

  public Response(Integer statusCode, String body) {
    this.statusCode = statusCode;
    this.body = body;
  }

  public Integer getStatusCode() {
    return statusCode;
  }

  public String getBody() {
    return body;
  }
}
