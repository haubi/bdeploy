package io.bdeploy.common.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.bdeploy.common.TempDirectory;
import io.bdeploy.common.TempDirectory.TempDir;

@ExtendWith(TempDirectory.class)
public class SecurityTest {

    private static final String TEST_TOKEN = "ewogICJjIjogIk1JSUR4RENDQXF5Z0F3SUJBZ0lFbFhsellUQU5CZ2txaGtpRzl3MEJBUXNGQURDQmtURVBNQTBHQTFVRUF3d0dUMkpsYkdsNE1TSXdJQVlEVlFRS0RCbFRVMGtnVTJOb3c2Um1aWElnU1ZRZ1UyOXNkWFJwYjI1ek1Sc3dHUVlEVlFRSkRCSkdjbWxsYzJGamFITjBjbUhEbjJVZ01UVXhEVEFMQmdOVkJCRU1CRGd4TVRReEVUQVBCZ05WQkFnTUNFWnlhV1Z6WVdOb01Rc3dDUVlEVlFRR0V3SkJWREVPTUF3R0ExVUVDd3dGVjBGTlFWTXdJQmNOTVRneE1qRXlNVFEwTURJeFdoZ1BNakEyTnpBNU1EWXhORFF3TWpGYU1JR1JNUTh3RFFZRFZRUUREQVpQWW1Wc2FYZ3hJakFnQmdOVkJBb01HVk5UU1NCVFkyakRwR1psY2lCSlZDQlRiMngxZEdsdmJuTXhHekFaQmdOVkJBa01Fa1p5YVdWellXTm9jM1J5WWNPZlpTQXhOVEVOTUFzR0ExVUVFUXdFT0RFeE5ERVJNQThHQTFVRUNBd0lSbkpwWlhOaFkyZ3hDekFKQmdOVkJBWVRBa0ZVTVE0d0RBWURWUVFMREFWWFFVMUJVekNDQVNJd0RRWUpLb1pJaHZjTkFRRUJCUUFEZ2dFUEFEQ0NBUW9DZ2dFQkFLbFhyWFA2WG1PdHdYMG1FUjlDKzBqWDBNWWVyUWV5YVM0Ymp6Nk05em9nQmhKSGJBZDgzT3VKVFo1WkVUNFBiWEoxWjNuekoyQjFMYTg3MGttLzdDQmlHd3N3ZzJuREUwaW9Hb0JmZ1hmNHQ5c0Z5Qk5ERXhkdHRsTHlINHBwYm1JcGllOWhGQ2Q1MGR4dmNzN28xd3lSV0NEclVnbWtnRVFtdDhlZFBXQjJua2xucXBTZEdmWHpCdUM4MEhiUHdRMUxCTkQwTUpDc2tvaCtCVTltK3R6cUQ4VWd2TkcyYURQQWM0SXU5SS9GZGpIR3Z4MlFZTDJqVjdvNzNHS3Z2dEFxdGhkMzBjcWVQck1oVFZHYzREd2pyYnM2VHJqMHpuWDVZeGY2QTNwOFEzZlpSNlA5U1hOemVjL3RpL1NwNkNHOXU4VFJMVUxRVGRkZ2RjNExwenNDQXdFQUFhTWdNQjR3RGdZRFZSMFBBUUgvQkFRREFnSzBNQXdHQTFVZEV3UUZNQU1CQWY4d0RRWUpLb1pJaHZjTkFRRUxCUUFEZ2dFQkFEbytwQUx0eSsyMDNINHppSFY1NHlkVHdiRXd2cStuSkFWSEwrQ28rbWpLWkhXM2hLWjhLaU1TZWw0WDhXUUlCMHlYUGlNWHZ3MzdadldNNEpkM00wY3lqWGQ2bmZTR0pVN2Q0N1doMkdyeXZGVWpJdkRhQTNVNkhCMVhKRERadkl5NHJZZXRGUWpJVnVxcHhaSzRHZG9YRWRmSCswU1MyQm1tYVBOWnN2MjF0WkdCTmM3SUlTZ2NzVWw0OFVCMk5UaHEwTXE0VnpITld0cjZya05Lb3FaMG5WS0FPUEZJbjlya3g4S2drajRHSWxGaE80bDdZOVFNS1N1UmJVdWpiSFVwM0RtbWt1d3NObVJpMHI3c2hub2E1bHJUMlgyL1kxVi9lUGtSOTNDTmczcTliSTlPaVhMSWkybDgvSDRxZUN2TzdoSWhQNVB6dE9nTDBlcVB4NDRcdTAwM2QiLAogICJ0IjogImV3b2dJQ0p3SWpvZ0ltVjNiMmRKUTBwd1pFTkpOa2xEU25SYVNGWnRaRU5KYzBOcFFXZEpiV3hvU1dwdlowMVVWVEJPUkZsNVRsUlplVTE2UVRWT2FYZExTVU5CYVdSdVZXbFBhVUY2VFVSbmVVNVVVVEZPYWtsNlRVUnJNa051TUZ4MU1EQXpaQ0lzQ2lBZ0luTWlPaUFpVlV0eU5FNUhVeXRGUzJaRGVsUldiVkpUWlNzemNtMHJiMk5tVFRkQ2EyeHZNR2R5ZFdwb1VWSTJNMGh4VEVOTFJtbEliazFGZFRaT1RFVndPRk5xVG1sMk4wTmhVWFE0T0V0VGRHOUNNR1J1VEZVMFVHRlpkVkYwU1dKb1FtZHRSbVpDVDNkTVZ6RTVZbGhYUm5adVprRjJlV0p5WW5wQmJsbHpNMDF2ZDBKSFpuWndNemRaVjI5T05FZDNhVE5TV2tGYU4wMXJRelpqZVZkMWVXNWxjV1ZYVkVkNFpHTkRhVE51TDBaWkwwWXdibTl5ZFdkdVpVdG1RelZvYVM5aE9IaG9jVVJYYmk5a09FUktaM2RIWjJKUGJISTRVMlpFUW5WclVWUlhUakpzYVhCbGN6RnpXbXRoV0c1NlFWaHhXbkpPYTA5TE56TlZkRmRIUzBSMVkyNTVibTVSZDNwSFZEaFlLMjlwV1dac2VTczBjSGhYVW1VMVRWaHpRVzQzUjBSQmFHMHpRWFpLUzJGWmJUQnFlRVpWUVdKb2JFcFpTaXRhTkhaelNHRTJPVmhwVWxobU1VZDROa2RDTlVGcE1HeDBlVXRCWEhVd01ETmtYSFV3TUROa0lncDkiCn0=";

    @Test
    void create(@TempDir Path tmp) throws GeneralSecurityException, IOException {
        Path ksPath = tmp.resolve("ks.jceks");

        SecurityHelper helper = SecurityHelper.getInstance();
        char[] pp = "hugo".toCharArray();
        helper.importSignaturePack(TEST_TOKEN, ksPath, pp);

        OnDiscKeyStore ks = new OnDiscKeyStore(ksPath, "hugo");
        assertNotNull(ks.getStore());
        assertArrayEquals(pp, ks.getPass());

        String retrieved = helper.getSignedToken(ks.getStore(), pp);
        ApiAccessToken a = helper.getVerifiedPayload(retrieved, ApiAccessToken.class, ks.getStore());

        assertNotNull(a);
        assertNotNull(a.it);
    }

}
