package org.drools.integrationtests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.drools.CommonTestMethodBase;
import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseConfiguration;
import org.drools.KnowledgeBaseFactory;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.conf.DeclarativeAgendaOption;
import org.drools.event.rule.ActivationCancelledEvent;
import org.drools.event.rule.ActivationCreatedEvent;
import org.drools.event.rule.AfterActivationFiredEvent;
import org.drools.event.rule.AgendaEventListener;
import org.drools.event.rule.AgendaGroupPoppedEvent;
import org.drools.event.rule.AgendaGroupPushedEvent;
import org.drools.event.rule.BeforeActivationFiredEvent;
import org.drools.event.rule.DebugAgendaEventListener;
import org.drools.event.rule.RuleFlowGroupActivatedEvent;
import org.drools.event.rule.RuleFlowGroupDeactivatedEvent;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.rule.Activation;
import org.drools.runtime.rule.FactHandle;
import org.junit.Test;

public class DeclarativeAgendaTest extends CommonTestMethodBase {
    
    @Test
    public void testSimpleBlockingUsingForall() {
        String str = "";
        str += "package org.domain.test \n";
        str += "import " + Activation.class.getName() + "\n";
        str += "global java.util.List list \n";
        str += "dialect 'mvel' \n";
        str += "rule rule1 @department(sales) salience -100 \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";
        str += "rule rule2 salience 200\n";
        str += "when \n";        
        str += "     $s : String( this == 'go1' ) \n";
        str += "     exists  Activation( department == 'sales' ) \n";  
        str += "     forall ( $a : Activation( department == 'sales' ) Activation( this == $a, active == false ) ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        ksession.insert( "go1" );
        ksession.fireAllRules();
        
        assertEquals( 2,
                      list.size() );
        assertEquals( "rule1:go1", list.get(0) );
        assertEquals( "rule2:go1", list.get(1) );

        ksession.dispose();
    }

    @Test
    public void testBasicBlockOnAnnotation() {
        String str = "";
        str += "package org.domain.test \n";
        str += "import " + Activation.class.getName() + "\n";
        str += "global java.util.List list \n";
        str += "dialect 'mvel' \n";
        str += "rule rule1 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";
        str += "rule rule2 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";
        str += "rule rule3 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";
        str += "rule blockerAllSalesRules @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go2' ) \n";
        str += "     $i : Activation( department == 'sales' ) \n";
        str += "then \n";
        str += "    list.add( $i.rule.name + ':' + $s  ); \n";
        str += "    kcontext.blockActivation( $i ); \n";
        str += "end \n";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        ksession.insert( "go1" );
        FactHandle go2 = ksession.insert( "go2" );
        ksession.fireAllRules();
        assertEquals( 3,
                      list.size() );
        assertTrue( list.contains( "rule1:go2" ) );
        assertTrue( list.contains( "rule2:go2" ) );
        assertTrue( list.contains( "rule3:go2" ) );

        list.clear();
        ksession.retract( go2 );
        ksession.fireAllRules();

        assertEquals( 3,
                      list.size() );
        assertTrue( list.contains( "rule1:go1" ) );
        assertTrue( list.contains( "rule2:go1" ) );
        assertTrue( list.contains( "rule3:go1" ) );

        ksession.dispose();
    }

    @Test
    public void testApplyBlockerFirst() {
        StatefulKnowledgeSession ksession = getStatefulKnowledgeSession();

        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        FactHandle go2 = ksession.insert( "go2" );
        FactHandle go1 = ksession.insert( "go1" );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go2" ) );

        list.clear();

        ksession.retract( go2 );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go1" ) );
    }

    @Test
    public void testApplyBlockerFirstWithFireAllRulesInbetween() {
        StatefulKnowledgeSession ksession = getStatefulKnowledgeSession();

        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        FactHandle go2 = ksession.insert( "go2" );
        ksession.fireAllRules();
        assertEquals( 0,
                      list.size() );

        FactHandle go1 = ksession.insert( "go1" );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go2" ) );

        list.clear();

        ksession.retract( go2 );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go1" ) );
    }

    @Test
    public void testApplyBlockerSecond() {
        StatefulKnowledgeSession ksession = getStatefulKnowledgeSession();

        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        FactHandle go1 = ksession.insert( "go1" );
        FactHandle go2 = ksession.insert( "go2" );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go2" ) );

        list.clear();

        ksession.retract( go2 );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go1" ) );
    }

    @Test
    public void testApplyBlockerSecondWithUpdate() {
        StatefulKnowledgeSession ksession = getStatefulKnowledgeSession();

        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        FactHandle go1 = ksession.insert( "go1" );
        FactHandle go2 = ksession.insert( "go2" );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go2" ) );

        list.clear();

        ksession.update( go2,
                         "go2" );
        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go2" ) );

        list.clear();

        ksession.retract( go2 );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go1" ) );
    }

    @Test
    public void testApplyBlockerSecondAfterUpdate() {
        StatefulKnowledgeSession ksession = getStatefulKnowledgeSession();

        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        FactHandle go1 = ksession.insert( "go1" );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go1" ) );

        list.clear();

        FactHandle go2 = ksession.insert( "go2" );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go2" ) );

        list.clear();

        ksession.update( go1,
                         "go1" );

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go2" ) );

        list.clear();

        ksession.retract( go2 );
        ksession.fireAllRules();

        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "rule1:go1" ) );
    }

    public StatefulKnowledgeSession getStatefulKnowledgeSession() {
        String str = "";
        str += "package org.domain.test \n";
        str += "import " + Activation.class.getName() + "\n";
        str += "global java.util.List list \n";
        str += "dialect 'mvel' \n";

        str += "rule rule1 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";

        str += "rule blockerAllSalesRules @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go2' ) \n";
        str += "     $i : Activation( department == 'sales' ) \n";
        str += "then \n";
        str += "    list.add( $i.rule.name + ':' + $s  ); \n";
        str += "    kcontext.blockActivation( $i ); \n";
        str += "end \n";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);

        return ksession;
    }

    @Test
    public void testMultipleBlockers() {
        String str = "";
        str += "package org.domain.test \n";
        str += "import " + Activation.class.getName() + "\n";
        str += "global java.util.List list \n";
        str += "dialect 'mvel' \n";

        str += "rule rule0 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go0' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";

        str += "rule blockerAllSalesRules1 @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "     $i : Activation( department == 'sales' ) \n";
        str += "then \n";
        str += "    list.add( $i.rule.name + ':' + $s  ); \n";
        str += "    kcontext.blockActivation( $i ); \n";
        str += "end \n";

        str += "rule blockerAllSalesRules2 @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go2' ) \n";
        str += "     $i : Activation( department == 'sales' ) \n";
        str += "then \n";
        str += "    list.add( $i.rule.name + ':' + $s  ); \n";
        str += "    kcontext.blockActivation( $i ); \n";
        str += "end \n";

        str += "rule blockerAllSalesRules3 @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go3' ) \n";
        str += "     $i : Activation( department == 'sales' ) \n";
        str += "then \n";
        str += "    list.add( $i.rule.name + ':' + $s  ); \n";
        str += "    kcontext.blockActivation( $i ); \n";
        str += "end \n";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        ksession.addEventListener( new DebugAgendaEventListener(  ) );
        FactHandle go0 = ksession.insert( "go0" );
        FactHandle go1 = ksession.insert( "go1" );
        FactHandle go2 = ksession.insert( "go2" );
        FactHandle go3 = ksession.insert( "go3" );

        ksession.fireAllRules();
        assertEquals( 3,
                      list.size() );
        assertTrue( list.contains( "rule0:go1" ) );
        assertTrue( list.contains( "rule0:go2" ) );
        assertTrue( list.contains( "rule0:go3" ) );

        list.clear();

        ksession.retract( go3 );
        ksession.fireAllRules();
        assertEquals( 0,
                      list.size() );

        ksession.retract( go2 );
        ksession.fireAllRules();
        assertEquals( 0,
                      list.size() );

        ksession.retract( go1 );
        ksession.fireAllRules();
        assertEquals( 1,
                      list.size() );

        assertTrue( list.contains( "rule0:go0" ) );
        ksession.dispose();
    }

    @Test
    public void testMultipleBlockersWithUnblockAll() {
        // This test is a bit wierd as it recurses. Maybe unblockAll is not feasible...
        String str = "";
        str += "package org.domain.test \n";
        str += "import " + Activation.class.getName() + "\n";
        str += "global java.util.List list \n";
        str += "dialect 'mvel' \n";

        str += "rule rule0 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go0' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";

        str += "rule blockerAllSalesRules1 @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "     $i : Activation( department == 'sales' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $i.rule.name + ':' + $s  ); \n";
        str += "    kcontext.blockActivation( $i ); \n";
        str += "end \n";

        str += "rule blockerAllSalesRules2 @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go2' ) \n";
        str += "     $i : Activation( department == 'sales' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $i.rule.name + ':' + $s  ); \n";
        str += "    kcontext.blockActivation( $i ); \n";
        str += "end \n";

        str += "rule blockerAllSalesRules3 @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go3' ) \n";
        str += "     $i : Activation( department == 'sales' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $i.rule.name + ':' + $s  ); \n";
        str += "    kcontext.blockActivation( $i ); \n";
        str += "end \n";

        str += "rule unblockAll @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go4' ) \n";
        str += "     $i : Activation( department == 'sales', active == true ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $i.rule.name + ':' + $s  ); \n";
        str += "    kcontext.unblockAllActivations( $i ); \n";
        str += "end \n";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        FactHandle go0 = ksession.insert( "go0" );
        FactHandle go1 = ksession.insert( "go1" );
        FactHandle go2 = ksession.insert( "go2" );
        FactHandle go3 = ksession.insert( "go3" );

        ksession.fireAllRules();
        assertEquals( 3,
                      list.size() );
        System.out.println( list );
        assertTrue( list.contains( "blockerAllSalesRules1:rule0:go1" ) );
        assertTrue( list.contains( "blockerAllSalesRules2:rule0:go2" ) );
        assertTrue( list.contains( "blockerAllSalesRules3:rule0:go3" ) );

        list.clear();

        FactHandle go4 = ksession.insert( "go4" );
        ksession.fireAllRules();
        assertEquals( 5,
                      list.size() );

        assertTrue( list.contains( "unblockAll:rule0:go4" ) );
        assertTrue( list.contains( "rule0:go0" ) );
        assertTrue( list.contains( "blockerAllSalesRules1:rule0:go1" ) );
        assertTrue( list.contains( "blockerAllSalesRules2:rule0:go2" ) );
        assertTrue( list.contains( "blockerAllSalesRules3:rule0:go3" ) );
    }

    @Test
    public void testIterativeUpdate() {
        String str = "";
        str += "package org.domain.test \n";
        str += "import " + Activation.class.getName() + "\n";
        str += "global java.util.List list \n";
        str += "dialect 'mvel' \n";

        str += "rule rule0 \n";
        str += "when \n";
        str += "     $s : String( this == 'rule0' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name ); \n";
        str += "end \n";

        str += "rule rule1 \n";
        str += "when \n";
        str += "     $s : String( this == 'rule1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name ); \n";
        str += "end \n";

        str += "rule rule2 \n";
        str += "when \n";
        str += "     $s : String( this == 'rule2' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name ); \n";
        str += "end \n";

        str += "rule blockerAllSalesRules1 @activationListener('direct') \n";
        str += "when \n";
        str += "     $l : List( ) \n";
        str += "     $i : Activation( rule.name == $l[0] ) \n";
        str += "then \n";
        //str += "   System.out.println( kcontext.rule.name  + ':' + $i ); \n";
        str += "    list.add( 'block:' + $i.rule.name  ); \n";
        str += "    kcontext.blockActivation( $i ); \n";
        str += "end \n";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);
        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );

        FactHandle rule0 = ksession.insert( "rule0" );
        FactHandle rule1 = ksession.insert( "rule1" );
        FactHandle rule2 = ksession.insert( "rule2" );

        ksession.fireAllRules();
        assertEquals( 3,
                      list.size() );
        assertTrue( list.contains( "rule0" ) );
        assertTrue( list.contains( "rule1" ) );
        assertTrue( list.contains( "rule2" ) );

        list.clear();

        ArrayList l = new ArrayList();
        ksession.update( rule0,
                         "rule0" );
        ksession.update( rule1,
                         "rule1" );
        ksession.update( rule2,
                         "rule2" );

        l.add( "rule0" );
        FactHandle lh = ksession.insert( l );

        ksession.fireAllRules();

        assertEquals( 3,
                      list.size() );
        assertTrue( list.contains( "block:rule0" ) );
        assertTrue( list.contains( "rule1" ) );
        assertTrue( list.contains( "rule2" ) );

        list.clear();

        ksession.update( rule0,
                         "rule0" );
        ksession.update( rule1,
                         "rule1" );
        ksession.update( rule2,
                         "rule2" );
        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "block:rule0" ) );

        list.clear();

        l.set( 0,
               "rule1" );
        ksession.update( lh,
                         l );
        ksession.fireAllRules();

        assertEquals( 3,
                      list.size() );
        assertTrue( list.contains( "rule0" ) );
        assertTrue( list.contains( "block:rule1" ) );
        assertTrue( list.contains( "rule2" ) );

        list.clear();

        ksession.update( rule0,
                         "rule0" );
        ksession.update( rule1,
                         "rule1" );
        ksession.update( rule2,
                         "rule2" );
        assertEquals( 1,
                      list.size() );
        assertTrue( list.contains( "block:rule1" ) );

        list.clear();

        l.set( 0,
               "rule2" );
        ksession.update( lh,
                         l );
        ksession.fireAllRules();

        assertEquals( 3,
                      list.size() );
        assertTrue( list.contains( "rule0" ) );
        assertTrue( list.contains( "rule1" ) );
        assertTrue( list.contains( "block:rule2" ) );
    }

    @Test
    public void testCancelActivation() {
        String str = "";
        str += "package org.domain.test \n";
        str += "import " + Activation.class.getName() + "\n";
        str += "global java.util.List list \n";
        str += "dialect 'mvel' \n";
        str += "rule rule1 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";
        str += "rule blockerAllSalesRules @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go2' ) \n";
        str += "     $i : Activation( department == 'sales' ) \n";
        str += "then \n";
        str += "    kcontext.cancelActivation( $i ); \n";
        str += "end \n";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);

        final List cancelled = new ArrayList();

        ksession.addEventListener( new AgendaEventListener() {

            public void beforeActivationFired(BeforeActivationFiredEvent event) {
            }

            public void agendaGroupPushed(AgendaGroupPushedEvent event) {
            }

            public void agendaGroupPopped(AgendaGroupPoppedEvent event) {
            }

            public void afterActivationFired(AfterActivationFiredEvent event) {
            }

            public void activationCreated(ActivationCreatedEvent event) {
            }

            public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
            }

            public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {
            }

            public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
            }

            public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {
            }
            
            public void activationCancelled(ActivationCancelledEvent event) {
                cancelled.add( event );
            }            
        } );

        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        ksession.insert( "go1" );
        FactHandle go2 = ksession.insert( "go2" );
        ksession.fireAllRules();
        assertEquals( 0,
                      list.size() );

        assertEquals( 1,
                      cancelled.size() );
        assertEquals( "rule1",
                      ((ActivationCancelledEvent) cancelled.get( 0 )).getActivation().getRule().getName() );
        ksession.dispose();
    }

    @Test
    public void testActiveInActiveChanges() {
        String str = "";
        str += "package org.domain.test \n";
        str += "import " + Activation.class.getName() + "\n";
        str += "global java.util.List list \n";
        str += "dialect 'mvel' \n";
        str += "rule rule1 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";
        str += "rule rule2 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";
        str += "rule rule3 @department(sales) \n";
        str += "when \n";
        str += "     $s : String( this == 'go1' ) \n";
        str += "then \n";
        str += "    list.add( kcontext.rule.name + ':' + $s ); \n";
        str += "end \n";
        str += "rule countActivateInActive @activationListener('direct') \n";
        str += "when \n";
        str += "     $s : String( this == 'go2' ) \n";
        str += "     $active : Number( this == 1 ) from accumulate( $a : Activation( department == 'sales', active == true ), count( $a ) )\n";
        str += "     $inActive : Number( this == 2 ) from  accumulate( $a : Activation( department == 'sales', active == false ), count( $a ) )\n";
        str += "then \n";
        str += "    list.add( $active + ':' + $inActive  ); \n";
        str += "    kcontext.halt( ); \n";
        str += "end \n";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);

        List list = new ArrayList();
        ksession.setGlobal( "list",
                            list );
        ksession.insert( "go1" );
        FactHandle go2 = ksession.insert( "go2" );
        ksession.fireAllRules();

        assertEquals( 3,
                      list.size() );
        assertTrue( list.contains( "1:2" ) );
        assertTrue( list.contains( "rule2:go1" ) );
        assertTrue( list.contains( "rule3:go1" ) );

        ksession.dispose();
    }

    @Test
    public void testCancelMultipleActivations() {
        String str = "package org.domain.test\n" +
                "import " + Activation.class.getName() + "\n" +
                "global java.util.List list\n" +
                "rule sales1 @department('sales')\n" +
                "when\n" +
                "    String( this == 'fireRules' )\n" +
                "then\n" +
                "    list.add(\"sales1\");\n" +
                "end\n" +
                "\n" +
                "rule sales2 @department('sales') \n" +
                "when\n" +
                "    String( this == 'fireRules' )\n" +
                "then\n" +
                "    list.add(\"sales2\");\n" +
                "end\n" +
                "\n" +
                "rule salesCancel @activationListener('direct')\n" +
                "when\n" +
                "    $i : Activation( department == 'sales' )\n" +
                "then\n" +
                "    kcontext.cancelActivation($i);\n" +
                "end";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);

        List list = new ArrayList();
        ksession.setGlobal( "list", list );

        ksession.insert("fireRules");
        ksession.fireAllRules();
        System.out.println(list);
        assertEquals(0, list.size());

        ksession.dispose();
    }

    @Test
    public void testCancelActivationOnInsertAndUpdate() {
        String str = "package org.domain.test\n" +
                "import " + Activation.class.getName() + "\n" +
                "global java.util.List list\n" +
                "rule sales1 @department('sales') @category('special')\n" +
                "salience 10\n" +
                "when\n" +
                "    String( this == 'fireRules' )\n" +
                "then\n" +
                "    list.add(\"sales1\");\n" +
                "end\n" +
                "\n" +
                "rule sales2 @department('sales') \n" +
                "when\n" +
                "    String( this == 'fireRules' )\n" +
                "then\n" +
                "    list.add(\"sales2\");\n" +
                "end\n" +
                "\n" +
                "rule salesCancel @activationListener('direct')\n" +
                "when\n" +
                "    String(this == 'fireCancelRule')\n" +
                "    $i : Activation( department == 'sales', category == 'special' )\n" +
                "then\n" +
                "    kcontext.cancelActivation($i);\n" +
                "end";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, str );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);

        List list = new ArrayList();
        ksession.setGlobal( "list", list );

        FactHandle fireRules = ksession.insert("fireRules");
        FactHandle fireCancelRule = ksession.insert("fireCancelRule");
        ksession.fireAllRules();
        assertEquals(1, list.size());

        ksession.update(fireRules, "fireRules");
        ksession.update(fireCancelRule, "fireCancelRule");
        ksession.fireAllRules();
        assertEquals(2, list.size());

        ksession.dispose();
    }


    @Test
    public void testExplicitUndercutWithDeclarativeAgenda() {

        String drl = "package org.drools.test;\n" +
                     "\n" +
                     "import " + Activation.class.getName() + "; \n" +
                     "\n" +
                     "global java.util.List list;\n" +
                     "\n" +
                     "declare Foo\n" +
                     " type : String\n" +
                     " value : double\n" +
                     "end\n" +
                     "\n" +
                     "declare Bar\n" +
                     " type : String\n" +
                     " total : double\n" +
                     "end\n" +
                     "\n" +
                     "\n" +
                     "rule \"Init\"\n" +
                     "when\n" +
                     "then\n" +
                     " insert( new Foo( \"first\", 10 ) );\n" +
                     " insert( new Foo( \"first\", 11 ) );\n" +
                     " insert( new Foo( \"second\", 20 ) );\n" +
                     " insert( new Foo( \"second\", 22 ) );\n" +
                     " insert( new Foo( \"third\", 30 ) );\n" +
                     " insert( new Foo( \"third\", 40 ) );\n" +
                     "end\n" +
                     "\n" +
                     "rule \"Accumulate\"\n" +
                     "salience 100\n" +
                     "dialect \"mvel\"\n" +
                     " when\n" +
                     " $type : String() from [ \"first\", \"second\", \"third\" ]\n" +
                     " accumulate ( Foo( type == $type, $value : value ),\n" +
                     " $total : sum( $value );\n" +
                     " $total > 0 )\n" +
                     " then\n" +
                     " insert(new Bar($type, $total));\n" +
                     "end\n" +
                     "\n" +
                     "rule \"handle all Bars of type first\"\n" +
                     "@Undercuts( others )\n" +
                     " when\n" +
                     " $bar : Bar( type == 'first', $total : total )\n" +
                     " then\n" +
                     " System.out.println( \"First bars \" + $total );\n" +
                     " list.add( $total );\n" +
                     "end\n" +
                     "\n" +
                     "rule \"handle all Bars of type second\"\n" +
                     "@Undercuts( others )\n" +
                     " when\n" +
                     " $bar : Bar( type == 'second', $total : total )\n" +
                     " then\n" +
                     " System.out.println( \"Second bars \" + $total );\n" +
                     " list.add( $total );\n" +
                     "end\n" +
                     "\n" +
                     "rule \"others\"\n" +
                     " when\n" +
                     " $bar : Bar( $total : total )\n" +
                     " then\n" +
                     " System.out.println( \"Other bars \" + $total );\n" +
                     " list.add( $total );\n" +
                     "end\n" +
                     "\n" +
                     "\n" +
                     "rule \"Undercut\"\n" +
                     "@activationListener( 'direct' ) \n" +
                     "when\n" +
                     " $m : Activation( $handles : factHandles )\n" +
                     " $v : Activation( rule.name == $m.Undercuts, factHandles == $handles )\n" +
                     "then\n" +
                     " System.out.println( \"Activation of rule \" + $m.getRule().getName() + \" overrides \" + $v.getRule().getName() + \" for tuple \" + $handles );\n" +
                     " kcontext.cancelActivation( $v );\n" +
                     "end\n" +
                     "\n" +
                     "\n";

        KnowledgeBaseConfiguration kconf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kconf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kconf, drl );
        StatefulKnowledgeSession ksession = createKnowledgeSession(kbase);

        List list = new ArrayList();
        ksession.setGlobal( "list", list );

        ksession.fireAllRules();

        assertEquals( Arrays.asList( 21.0, 42.0, 70.0 ), list );

        ksession.dispose();
    }

    @Test
    public void testDeclarativeAgendaAvoidRefireAfterUnblock() {
        String drl =
                "package org.drools.compiler.integrationtests\n" +
                "\n" +
                "import " + Activation.class.getName() + "; \n" +
                "import java.util.List\n" +
                "\n" +
                "global List list\n" +
                "\n" +
                "rule startAgenda\n" +
                "salience 100\n" +
                "when\n" +
                " String( this == 'startAgenda' )\n" +
                "then\n" +
                " drools.getKnowledgeRuntime().getAgenda().getAgendaGroup(\"agenda\").setFocus();\n" +
                " list.add(kcontext.getRule().getName());\n" +
                "end\n" +
                "\n" +
                "rule sales @department('sales') salience 10\n" +
                "agenda-group \"agenda\"\n" +
                "when\n" +
                " $s : String( this == 'fireRules' )\n" +
                "then\n" +
                " list.add(kcontext.getRule().getName());\n" +
                "end\n" +
                "\n" +
                "rule salesBlocker\n" +
                "when\n" +
                " $s : String( this == 'fireBlockerRule' )\n" +
                " $i : Activation( department == 'sales' )\n" +
                "then\n" +
                " kcontext.blockActivation( $i );\n" +
                " list.add(kcontext.getRule().getName());\n" +
                "end\n";

        KnowledgeBaseConfiguration kbConf = KnowledgeBaseFactory.newKnowledgeBaseConfiguration();
        kbConf.setOption( DeclarativeAgendaOption.ENABLED );
        KnowledgeBase kbase = loadKnowledgeBaseFromString( kbConf, drl );

        StatefulKnowledgeSession ksession = kbase.newStatefulKnowledgeSession();
        List list = new ArrayList( );
        ksession.setGlobal( "list", list );

        // first run
        ksession.insert("startAgenda");
        ksession.insert("fireRules");
        FactHandle fireBlockerRule = ksession.insert("fireBlockerRule");
        ksession.fireAllRules();
        String[] expected = { "startAgenda", "sales", "salesBlocker" };

        assertEquals(expected.length, list.size());
        for (int i = 0; i < list.size(); i++) {
            assertEquals(expected[i], list.get(i));
        }

        // second run
        ksession.retract(fireBlockerRule);
        list.clear();
        ksession.fireAllRules();
        System.out.println(list);
        assertEquals(0, list.size());
    }

}