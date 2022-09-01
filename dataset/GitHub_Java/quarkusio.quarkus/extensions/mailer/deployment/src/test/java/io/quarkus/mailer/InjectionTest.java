package io.quarkus.mailer;

import java.util.concurrent.CompletionStage;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.mailer.MailTemplate.MailTemplateInstance;
import io.quarkus.qute.api.CheckedTemplate;
import io.quarkus.qute.api.ResourcePath;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.ext.mail.MailClient;

@SuppressWarnings("WeakerAccess")
public class InjectionTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(JavaArchive.class)
                    .addClasses(BeanUsingAxleMailClient.class, BeanUsingBareMailClient.class, BeanUsingRxClient.class)
                    .addClasses(BeanUsingBlockingMailer.class, BeanUsingReactiveMailer.class)
                    .addClasses(MailTemplates.class)
                    .addAsResource("mock-config.properties", "application.properties")
                    .addAsResource(new StringAsset(""
                            + "<html>{name}</html>"), "templates/test1.html")
                    .addAsResource(new StringAsset(""
                            + "{name}"), "templates/test1.txt")
                    .addAsResource(new StringAsset(""
                            + "<html>{name}</html>"), "templates/MailTemplates/testNative.html")
                    .addAsResource(new StringAsset(""
                            + "{name}"), "templates/MailTemplates/testNative.txt")
                    .addAsResource(new StringAsset(""
                            + "<html>{name}</html>"), "templates/mails/test2.html"));

    @Inject
    BeanUsingAxleMailClient beanUsingBare;

    @Inject
    BeanUsingBareMailClient beanUsingAxle;

    @Inject
    BeanUsingRxClient beanUsingRx;

    @Inject
    BeanUsingMutinyClient beanUsingMutiny;

    @Inject
    BeanUsingReactiveMailer beanUsingReactiveMailer;

    @Inject
    BeanUsingLegacyReactiveMailer beanUsingLegacyReactiveMailer;

    @Inject
    BeanUsingBlockingMailer beanUsingBlockingMailer;

    @Inject
    MailTemplates templates;

    @Test
    public void testInjection() {
        beanUsingMutiny.verify();
        beanUsingAxle.verify();
        beanUsingBare.verify();
        beanUsingRx.verify();
        beanUsingBlockingMailer.verify();
        beanUsingReactiveMailer.verify().toCompletableFuture().join();
        beanUsingLegacyReactiveMailer.verify().toCompletableFuture().join();
        templates.send1();
        templates.send2().toCompletableFuture().join();
        templates.sendNative().toCompletableFuture().join();
    }

    @ApplicationScoped
    static class BeanUsingBareMailClient {

        @Inject
        MailClient client;

        void verify() {
            Assertions.assertNotNull(client);
        }
    }

    @ApplicationScoped
    static class BeanUsingAxleMailClient {

        @Inject
        io.vertx.axle.ext.mail.MailClient client;

        void verify() {
            Assertions.assertNotNull(client);
        }
    }

    @ApplicationScoped
    static class BeanUsingRxClient {

        @Inject
        io.vertx.reactivex.ext.mail.MailClient client;

        void verify() {
            Assertions.assertNotNull(client);
        }
    }

    @ApplicationScoped
    static class BeanUsingMutinyClient {

        @Inject
        io.vertx.mutiny.ext.mail.MailClient client;

        void verify() {
            Assertions.assertNotNull(client);
        }
    }

    @ApplicationScoped
    static class BeanUsingReactiveMailer {

        @Inject
        io.quarkus.mailer.reactive.ReactiveMailer mailer;

        CompletionStage<Void> verify() {
            return mailer.send(Mail.withText("quarkus@quarkus.io", "test mailer", "reactive test!"))
                    .subscribeAsCompletionStage();
        }
    }

    @ApplicationScoped
    static class BeanUsingLegacyReactiveMailer {

        @Inject
        ReactiveMailer mailer;

        CompletionStage<Void> verify() {
            return mailer.send(Mail.withText("quarkus@quarkus.io", "test mailer", "reactive test!"));
        }
    }

    @ApplicationScoped
    static class BeanUsingBlockingMailer {

        @Inject
        Mailer mailer;

        void verify() {
            mailer.send(Mail.withText("quarkus@quarkus.io", "test mailer", "blocking test!"));
        }
    }

    @Singleton
    static class MailTemplates {

        @CheckedTemplate
        static class Templates {
            public static native MailTemplateInstance testNative(String name);
        }

        @Inject
        MailTemplate test1;

        @ResourcePath("mails/test2")
        MailTemplate testMail;

        CompletionStage<Void> send1() {
            return test1.to("quarkus@quarkus.io").subject("Test").data("name", "John").send();
        }

        CompletionStage<Void> send2() {
            return testMail.to("quarkus@quarkus.io").subject("Test").data("name", "Lu").send();
        }

        CompletionStage<Void> sendNative() {
            return Templates.testNative("John").to("quarkus@quarkus.io").subject("Test").send();
        }
    }
}
