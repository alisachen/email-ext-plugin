package hudson.plugins.emailext;

import com.gargoylesoftware.htmlunit.ElementNotFoundException;
import com.gargoylesoftware.htmlunit.html.*;
import hudson.Functions;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import hudson.util.ReflectionUtils;
import jenkins.model.Jenkins;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class ExtendedEmailPublisherDescriptorTest {
    
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testGlobalConfigDefaultState() throws Exception {
        HtmlPage page = j.createWebClient().goTo("configure");

        assertEquals("Should be at the Configure System page",
                "Configure System [Jenkins]", page.getTitleText());

        // default content type select control
        HtmlSelect contentType = page.getElementByName("ext_mailer_default_content_type");
        assertNotNull("Content type selection should be present", contentType);
        assertEquals("Plain text should be selected by default",
                "text/plain", contentType.getSelectedOptions().get(0).getValueAttribute());

        HtmlCheckBoxInput useListId = page.getElementByName("ext_mailer_use_list_id");
        assertNotNull("Use List ID should be present", useListId);
        assertFalse("Use List ID should not be checked by default", useListId.isChecked());

        HtmlCheckBoxInput precedenceBulk = page.getElementByName("ext_mailer_add_precedence_bulk");
        assertNotNull("Precedence Bulk should be present", precedenceBulk);
        assertFalse("Add precedence bulk should not be checked by default",
                precedenceBulk.isChecked());

        HtmlTextInput defaultRecipients = page.getElementByName("ext_mailer_default_recipients");
        assertNotNull("Default Recipients should be present", defaultRecipients);
        assertEquals("Default recipients should be blank by default", 
                "", defaultRecipients.getText());

        HtmlTextInput defaultReplyTo = page.getElementByName("ext_mailer_default_replyto");
        assertNotNull("Default Reply-to should be present", defaultReplyTo);
        assertEquals("Default Reply-To should be blank by default", 
                "", defaultReplyTo.getText());

        HtmlTextInput emergencyReroute = page.getElementByName("ext_mailer_emergency_reroute");
        assertNotNull("Emergency Reroute should be present", emergencyReroute);
        assertEquals("Emergency Reroute should be blank by default", 
                "", emergencyReroute.getText());
        
        HtmlTextInput allowedDomains = page.getElementByName("ext_mailer_allowed_domains");
        assertNotNull("Allowed Domains should be present", allowedDomains);
        assertEquals("Allowed Domains should be blang by default", 
                "", allowedDomains.getText());

        HtmlTextInput excludedRecipients = page.getElementByName("ext_mailer_excluded_committers");
        assertNotNull("Excluded Recipients should be present", excludedRecipients);
        assertEquals("Excluded Recipients should be blank by default",
                "", excludedRecipients.getText());

        HtmlTextInput defaultSubject = page.getElementByName("ext_mailer_default_subject");
        assertNotNull("Default Subject should be present", defaultSubject);
        assertEquals("Default Subject should be set", 
                "$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!", 
                defaultSubject.getText());
        
        HtmlTextInput maxAttachmentSize = page.getElementByName("ext_mailer_max_attachment_size");
        assertNotNull("Max attachment size should be present", maxAttachmentSize);
        assertEquals("Max attachment size should be blank by default",
                "", maxAttachmentSize.getText());
        
        HtmlTextArea defaultContent = page.getElementByName("ext_mailer_default_body");
        assertNotNull("Default content should be present", defaultContent);
        assertEquals("Default content should be set by default",
                "$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS:\n\nCheck console output at $BUILD_URL to view the results.",
                defaultContent.getText());
        
        HtmlCheckBoxInput debugMode = page.getElementByName("ext_mailer_debug_mode");
        assertNotNull("Debug mode should be present", debugMode);
        assertFalse("Debug mode should not be checked by default", debugMode.isChecked());
        
        try {
            page.getElementByName("defaultClasspath");
            fail("defaultClasspath section should not be present");
        } catch (ElementNotFoundException e) {}
    }
    
    @Test
    @Issue("JENKINS-20030")
    public void testGlobalConfigSimpleRoundTrip() throws Exception {
        ExtendedEmailPublisherDescriptor descriptor = j.jenkins.getDescriptorByType(ExtendedEmailPublisherDescriptor.class);        
        HtmlPage page = j.createWebClient().goTo("configure");
        HtmlTextInput defaultRecipients = page.getElementByName("ext_mailer_default_recipients");
        defaultRecipients.setValueAttribute("mickey@disney.com");
        j.submit(page.getFormByName("config"));       
        
        assertEquals("mickey@disney.com", descriptor.getDefaultRecipients());
    }

    @Test
    @Issue("JENKINS-20133")
    public void testPrecedenceBulkSettingRoundTrip() throws Exception {
        ExtendedEmailPublisherDescriptor descriptor = j.jenkins.getDescriptorByType(ExtendedEmailPublisherDescriptor.class);
        HtmlPage page = j.createWebClient().goTo("configure");
        HtmlCheckBoxInput addPrecedenceBulk = page.getElementByName("ext_mailer_add_precedence_bulk");
        addPrecedenceBulk.setChecked(true);
        j.submit(page.getFormByName("config"));

        assertEquals(true, descriptor.getPrecedenceBulk());
    }

    @Test
    @Issue("JENKINS-20133")
    public void testListIDRoundTrip() throws Exception {
        ExtendedEmailPublisherDescriptor descriptor = j.jenkins.getDescriptorByType(ExtendedEmailPublisherDescriptor.class);
        HtmlPage page = j.createWebClient().goTo("configure");
        HtmlCheckBoxInput useListId = page.getElementByName("ext_mailer_use_list_id");
        useListId.setChecked(true);
        HtmlTextInput listId = page.getElementByName("ext_mailer_list_id");
        listId.setValueAttribute("hammer");

        j.submit(page.getFormByName("config"));

        assertEquals("hammer", descriptor.getListId());
    }

    @Test
    public void testAdvancedProperties() throws Exception {
        ExtendedEmailPublisherDescriptor descriptor = j.jenkins.getDescriptorByType(ExtendedEmailPublisherDescriptor.class);
        HtmlPage page = j.createWebClient().goTo("configure");
        HtmlTextArea advProperties = page.getElementByName("ext_mailer_adv_properties");
        advProperties.setText("mail.smtp.starttls.enable=true");
        j.submit(page.getFormByName("config"));

        assertEquals("mail.smtp.starttls.enable=true", descriptor.getAdvProperties());
    }

    @Test
    public void managePermissionShouldAccess() {
        Permission jenkinsManage;
        try {
            jenkinsManage = getJenkinsManage();
        } catch (Exception e) {
            Assume.assumeTrue("Jenkins baseline is too old for this test (requires Jenkins.MANAGE)", false);
            return;
        }
        final String USER = "user";
        final String MANAGER = "manager";
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                // Read access
                .grant(Jenkins.READ).everywhere().to(USER)

                // Read and Manage
                .grant(Jenkins.READ).everywhere().to(MANAGER)
                .grant(jenkinsManage).everywhere().to(MANAGER)
        );
        try (ACLContext c = ACL.as(User.getById(USER, true))) {
            Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
            Assert.assertTrue("Global configuration should not be accessible to READ users", descriptors.size() == 0);
        }
        try (ACLContext c = ACL.as(User.getById(MANAGER, true))) {
            Collection<Descriptor> descriptors = Functions.getSortedDescriptorsForGlobalConfigUnclassified();
            Optional<Descriptor> found = descriptors.stream().filter(descriptor -> descriptor instanceof ExtendedEmailPublisherDescriptor).findFirst();
            Assert.assertTrue("Global configuration should be accessible to MANAGE users", found.isPresent());
        }
    }

    // TODO: remove when Jenkins core baseline is 2.222+
    private Permission getJenkinsManage() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        // Jenkins.MANAGE is available starting from Jenkins 2.222 (https://jenkins.io/changelog/#v2.222). See JEP-223 for more info
        return (Permission) ReflectionUtils.getPublicProperty(Jenkins.get(), "MANAGE");
    }

}
