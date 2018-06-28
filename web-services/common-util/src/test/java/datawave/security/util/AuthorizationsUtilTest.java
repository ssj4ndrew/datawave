package datawave.security.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import datawave.security.authorization.DatawavePrincipal;
import datawave.security.authorization.DatawaveUser;
import datawave.security.authorization.DatawaveUser.UserType;
import datawave.security.authorization.SubjectIssuerDNPair;
import datawave.security.util.DnUtils.NpeUtils;
import org.apache.accumulo.core.security.Authorizations;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.List;

public class AuthorizationsUtilTest {
    private static final String USER_DN = "userDN";
    private static final String ISSUER_DN = "issuerDN";
    private String methodAuths;
    private HashSet<Set<String>> userAuths;
    private DatawavePrincipal principal;
    
    @Before
    public void initialize() {
        System.setProperty(NpeUtils.NPE_OU_PROPERTY, "iamnotaperson");
        methodAuths = "A,C";
        userAuths = new HashSet<>();
        userAuths.add(Sets.newHashSet("A", "C", "D"));
        userAuths.add(Sets.newHashSet("A", "B", "E"));
        
        SubjectIssuerDNPair userDN = SubjectIssuerDNPair.of(USER_DN, ISSUER_DN);
        SubjectIssuerDNPair p1dn = SubjectIssuerDNPair.of("entity1UserDN", "entity1IssuerDN");
        SubjectIssuerDNPair p2dn = SubjectIssuerDNPair.of("entity2UserDN", "entity2IssuerDN");
        
        DatawaveUser user = new DatawaveUser(userDN, UserType.USER, Sets.newHashSet("A", "C", "D"), null, null, System.currentTimeMillis());
        DatawaveUser p1 = new DatawaveUser(p1dn, UserType.SERVER, Sets.newHashSet("A", "B", "E"), null, null, System.currentTimeMillis());
        DatawaveUser p2 = new DatawaveUser(p2dn, UserType.SERVER, Sets.newHashSet("A", "F", "G"), null, null, System.currentTimeMillis());
        
        principal = new DatawavePrincipal(Lists.newArrayList(user, p1, p2));
    }
    
    @Test
    public void testMergeAuthorizations() {
        HashSet<Authorizations> expected = Sets.newHashSet(new Authorizations("A", "C"), new Authorizations("A"));
        assertEquals(expected, AuthorizationsUtil.mergeAuthorizations(methodAuths, userAuths));
    }
    
    @Test
    public void testDowngradeAuthorizations() {
        HashSet<Authorizations> expected = Sets.newHashSet(new Authorizations("A", "C"), new Authorizations("A", "B", "E"), new Authorizations("A", "F", "G"));
        assertEquals(expected, AuthorizationsUtil.getDowngradedAuthorizations(methodAuths, principal));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testDowngradeAuthorizationsWithMissingAuths() {
        HashSet<Authorizations> expected = Sets.newHashSet(new Authorizations("A", "C"), new Authorizations("A", "B", "E"), new Authorizations("A", "F", "G"));
        assertEquals(expected, AuthorizationsUtil.getDowngradedAuthorizations("X,Y,Z", principal));
    }
    
    @Test
    public void testDowngradeAuthorizationsWithNoRequestedAuths() {
        HashSet<Authorizations> expected = Sets.newHashSet(new Authorizations("A", "C", "D"), new Authorizations("A", "B", "E"), new Authorizations("A", "F",
                        "G"));
        assertEquals(expected, AuthorizationsUtil.getDowngradedAuthorizations("", principal));
    }
    
    @Test
    public void testDowngradeAuthorizationsWithNullRequestedAuths() {
        HashSet<Authorizations> expected = Sets.newHashSet(new Authorizations("A", "C", "D"), new Authorizations("A", "B", "E"), new Authorizations("A", "F",
                        "G"));
        assertEquals(expected, AuthorizationsUtil.getDowngradedAuthorizations(null, principal));
    }
    
    @Test
    public void testDowngradeUserAuthorizations() {
        String expected = "C,A";
        assertEquals(new HashSet<String>(Arrays.asList(expected.split(","))),
                        new HashSet<String>(Arrays.asList(AuthorizationsUtil.downgradeUserAuths(principal, methodAuths).split(","))));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testDowngradeUserAuthorizationsWithMissingAuths() {
        AuthorizationsUtil.downgradeUserAuths(principal, "X,Y,Z");
    }
    
    @Test
    public void testDowngradeUserAuthorizationsWithNoRequestedAuths() {
        String expected = "C,D,A";
        assertEquals(new HashSet<String>(Arrays.asList(expected.split(","))),
                        new HashSet<String>(Arrays.asList(AuthorizationsUtil.downgradeUserAuths(principal, "").split(","))));
    }
    
    @Test
    public void testDowngradeUserAuthorizationsWithNullRequestedAuths() {
        String expected = "C,D,A";
        assertEquals(new HashSet<String>(Arrays.asList(expected.split(","))),
                        new HashSet<String>(Arrays.asList(AuthorizationsUtil.downgradeUserAuths(principal, null).split(","))));
    }
    
    @Test
    public void testUserAuthsFirstInMergedSet() {
        HashSet<Authorizations> mergedAuths = AuthorizationsUtil.getDowngradedAuthorizations(methodAuths, principal);
        assertEquals(3, mergedAuths.size());
        assertEquals("Merged user authorizations were not first in the return set", new Authorizations("A", "C"), mergedAuths.iterator().next());
    }
    
    @Test
    public void testUnionAuthorizations() {
        assertEquals(new Authorizations("A", "C"), AuthorizationsUtil.union(new Authorizations("A", "C"), new Authorizations("A")));
    }
    
    @Test
    public void testUnionWithEmptyAuthorizations() {
        assertEquals(new Authorizations("A", "C"), AuthorizationsUtil.union(new Authorizations("A", "C"), new Authorizations()));
    }
    
    @Test
    public void testUnionWithBothEmptyAuthorizations() {
        assertEquals(new Authorizations(), AuthorizationsUtil.union(new Authorizations(), new Authorizations()));
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testUserRequestsAuthTheyDontHave() {
        // This is the case where we could throw an error or write something to the logs
        methodAuths = "A,C,F";
        AuthorizationsUtil.mergeAuthorizations(methodAuths, userAuths);
        fail("Exception not thrown!");
    }
    
    @Test
    public void testMethodAuthsIsNull() {
        HashSet<Authorizations> expected = new HashSet<>();
        for (Set<String> auths : userAuths) {
            expected.add(new Authorizations(auths.toArray(new String[auths.size()])));
        }
        assertEquals(expected, AuthorizationsUtil.mergeAuthorizations(null, userAuths));
    }
    
    @Test
    public void testUserAuthsIsNull() {
        assertEquals(Collections.singleton(new Authorizations()), AuthorizationsUtil.mergeAuthorizations(methodAuths, null));
    }
    
    @Test
    public void testBothMethodAndUserAuthsNull() {
        assertEquals(Collections.singleton(new Authorizations()), AuthorizationsUtil.mergeAuthorizations(null, null));
    }
    
    @Test
    public void testMinimizeWithSubset() {
        ArrayList<Authorizations> authSets = Lists.newArrayList(new Authorizations("A", "B", "C", "D"), new Authorizations("C", "B"), new Authorizations("A",
                        "B", "C"), new Authorizations("B", "C", "D", "E"));
        Collection<Authorizations> expected = Collections.singleton(new Authorizations("B", "C"));
        
        assertEquals(expected, AuthorizationsUtil.minimize(authSets));
    }
    
    @Test
    public void testMinimizeWithNoSubset() {
        LinkedHashSet<Authorizations> expected = new LinkedHashSet<>();
        expected.add(new Authorizations("A", "B", "C", "D"));
        expected.add(new Authorizations("B", "C", "F"));
        expected.add(new Authorizations("A", "B", "C"));
        expected.add(new Authorizations("B", "C", "D", "E"));
        
        assertEquals(expected, AuthorizationsUtil.minimize(expected));
    }
    
    @Test
    public void testMinimizeWithDupsButNoSubset() {
        ArrayList<Authorizations> authSets = Lists.newArrayList(new Authorizations("A", "B", "C", "D"), new Authorizations("B", "C", "F"), new Authorizations(
                        "A", "B", "C", "D"), new Authorizations("B", "C", "D", "E"));
        
        LinkedHashSet<Authorizations> expected = new LinkedHashSet<>();
        expected.add(new Authorizations("A", "B", "C", "D"));
        expected.add(new Authorizations("B", "C", "F"));
        expected.add(new Authorizations("B", "C", "D", "E"));
        assertEquals(expected, AuthorizationsUtil.minimize(authSets));
    }
    
    @Test
    public void testBuilidAuthorizationString() {
        Collection<Collection<String>> auths = new HashSet<>();
        List<String> authsList = Arrays.asList("A", "B", "C", "D", "E", "F", "G", "H", "A", "E", "I", "J");
        
        HashSet<String> uniqAuths = new HashSet<>(authsList);
        
        auths.add(authsList.subList(0, 4));
        auths.add(authsList.subList(4, 8));
        auths.add(authsList.subList(8, 12));
        uniqAuths.removeAll(Arrays.asList(AuthorizationsUtil.buildAuthorizationString(auths).split(",")));
        assertTrue(uniqAuths.isEmpty());
    }
    
    @Test
    public void testBuildUserAuthorizationsString() {
        String expected = new Authorizations("A", "C", "D").toString();
        assertEquals(expected, AuthorizationsUtil.buildUserAuthorizationString(principal));
    }
}