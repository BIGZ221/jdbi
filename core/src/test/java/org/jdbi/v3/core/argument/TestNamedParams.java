/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jdbi.v3.core.argument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Something;
import org.jdbi.v3.core.junit5.H2DatabaseExtension;
import org.jdbi.v3.core.statement.Query;
import org.jdbi.v3.core.statement.Update;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class TestNamedParams {

    @RegisterExtension
    public H2DatabaseExtension h2Extension = H2DatabaseExtension.instance().withInitializer(H2DatabaseExtension.SOMETHING_INITIALIZER);

    @Test
    public void testInsert() {
        Handle h = h2Extension.getSharedHandle();
        Update insert = h.createUpdate("insert into something (id, name) values (:id, :name)");
        insert.bind("id", 1);
        insert.bind("name", "Brian");
        int count = insert.execute();
        assertThat(count).isOne();
    }

    @Test
    public void testDemo() {
        Handle h = h2Extension.getSharedHandle();
        h.createUpdate("insert into something (id, name) values (:id, :name)")
            .bind("id", 1)
            .bind("name", "Brian")
            .execute();
        h.execute("insert into something (id, name) values (?, ?)", 2, "Eric");
        h.execute("insert into something (id, name) values (?, ?)", 3, "Erin");

        List<Something> r = h.createQuery("select id, name from something "
                + "where name like :name "
                + "order by id")
            .bind("name", "Eri%")
            .mapToBean(Something.class)
            .list();

        assertThat(r).extracting(Something::getId).containsExactly(2, 3);
    }

    @Test
    public void testBeanPropertyBinding() {
        Handle h = h2Extension.getSharedHandle();
        Something original = new Something(0, "Keith");

        assertThat(h
            .createUpdate("insert into something (id, name) values (:id, :name)")
            .bindBean(original)
            .execute())
            .isOne();

        assertThat(h
            .select("select * from something where id = ?", original.getId())
            .mapToBean(Something.class)
            .one())
            .isEqualTo(original);
    }

    @Test
    public void testBeanPropertyPrefixBinding() {
        Handle h = h2Extension.getSharedHandle();
        Something original = new Something(0, "Keith");

        assertThat(h
            .createUpdate("insert into something (id, name) values (:my.id, :my.name)")
            .bindBean("my", original)
            .execute())
            .isOne();

        assertThat(h
            .select("select * from something where id = ?", original.getId())
            .mapToBean(Something.class)
            .one())
            .isEqualTo(original);
    }

    @Test
    public void testBeanPropertyNestedBinding() {
        Handle h = h2Extension.getSharedHandle();

        Something thing = new Something(0, "Keith");

        assertThat(h
            .createUpdate("insert into something (id, name) values (:my.nested.id, :my.nested.name)")
            .bindBean("my", new NestsSomething(thing))
            .execute()).isOne();

        assertThat(h
            .select("select * from something where id = ?", thing.getId())
            .mapToBean(Something.class)
            .one())
            .isEqualTo(thing);
    }

    public static class NestsSomething {

        private final Something nested;

        NestsSomething(Something nested) {
            this.nested = nested;
        }

        public Something getNested() {
            return nested;
        }
    }

    @Test
    public void testFieldsBinding() {
        Handle h = h2Extension.getSharedHandle();

        assertThat(h
            .createUpdate("insert into something (id, name) values (:id, :name)")
            .bindFields(new PublicFields(0, "Keith"))
            .execute())
            .isOne();

        assertThat(h
            .select("select * from something where id = ?", 0)
            .mapToBean(Something.class)
            .one())
            .isEqualTo(new Something(0, "Keith"));
    }

    @Test
    public void testFieldsPrefixBinding() {
        Handle h = h2Extension.getSharedHandle();

        assertThat(h
            .createUpdate("insert into something (id, name) values (:my.id, :my.name)")
            .bindFields("my", new PublicFields(0, "Keith"))
            .execute())
            .isOne();

        assertThat(h
            .select("select * from something where id = ?", 0)
            .mapToBean(Something.class)
            .one())
            .isEqualTo(new Something(0, "Keith"));
    }

    @Test
    public void testFieldsNestedBinding() {
        Handle h = h2Extension.getSharedHandle();

        assertThat(h
            .createUpdate("insert into something (id, name) values (:my.nested.id, :my.nested.name)")
            .bindFields("my", new Object() {
                @SuppressWarnings("unused")
                public final PublicFields nested = new PublicFields(0, "Keith");
            })
            .execute())
            .isOne();

        assertThat(h
            .select("select * from something where id = ?", 0)
            .mapToBean(Something.class)
            .one())
            .isEqualTo(new Something(0, "Keith"));
    }

    @Test
    public void testFieldsNestedBindingToNull() {
        Handle h = h2Extension.getSharedHandle();

        assertThat(h
            .createUpdate("insert into something (id, name) values (0, :my.nested?.name)")
            .bindFields("my", new Object() {
                @SuppressWarnings("unused")
                public final PublicFields nested = null;
            })
            .execute())
            .isOne();

        assertThat(h
            .select("select * from something where id = ?", 0)
            .mapToBean(Something.class)
            .one())
            .isEqualTo(new Something(0, null));
    }

    public static class FunctionsNestedBinding {

        public NoArgFunctions nested() {
            return new NoArgFunctions(0, "Keith");
        }
    }

    public static class PublicFields {

        public int id = 0;
        public String name = "Keith";

        public PublicFields(int id, String name) {

            this.id = id;
            this.name = name;
        }
    }

    @Test
    public void testFunctionsBinding() {
        Handle h = h2Extension.getSharedHandle();

        assertThat(h
            .createUpdate("insert into something (id, name) values (:id, :name)")
            .bindMethods(new NoArgFunctions(0, "Keith"))
            .execute())
            .isOne();

        assertThat(h
            .select("select * from something where id = ?", 0)
            .mapToBean(Something.class)
            .one())
            .isEqualTo(new Something(0, "Keith"));
    }

    @Test
    public void testFunctionsPrefixBinding() {
        Handle h = h2Extension.getSharedHandle();

        assertThat(h
            .createUpdate("insert into something (id, name) values (:my.id, :my.name)")
            .bindMethods("my", new NoArgFunctions(0, "Keith"))
            .execute())
            .isOne();

        assertThat(h
            .select("select * from something where id = ?", 0)
            .mapToBean(Something.class)
            .one())
            .isEqualTo(new Something(0, "Keith"));
    }

    @Test
    public void testFunctionsNestedBinding() {
        Handle h = h2Extension.getSharedHandle();

        assertThat(h
            .createUpdate("insert into something (id, name) values (:my.nested.id, :my.nested.name)")
            .bindMethods("my", new FunctionsNestedBinding())
            .execute())
            .isOne();

        assertThat(h
            .select("select * from something where id = ?", 0)
            .mapToBean(Something.class)
            .one())
            .isEqualTo(new Something(0, "Keith"));
    }

    public static class NoArgFunctions {

        private final int i;
        private final String s;

        public NoArgFunctions(int i, String s) {
            this.i = i;
            this.s = s;
        }

        public int id() {
            return i;
        }

        public String name() {
            return s;
        }
    }

    @Test
    public void testMapKeyBinding() {
        Handle h = h2Extension.getSharedHandle();
        Update s = h.createUpdate("insert into something (id, name) values (:id, :name)");
        Map<String, Object> args = new HashMap<>();
        args.put("id", 0);
        args.put("name", "Keith");
        s.bindMap(args);
        int insertCount = s.execute();

        Query q = h.createQuery("select * from something where id = :id").bind("id", 0);
        final Something fromDb = q.mapToBean(Something.class).one();

        assertThat(insertCount).isOne();
        assertThat(fromDb).extracting(Something::getId, Something::getName).containsExactly(0, "Keith");
    }

    @Test
    public void testCascadedLazyArgs() {
        Handle h = h2Extension.getSharedHandle();
        Update s = h.createUpdate("insert into something (id, name) values (:id, :name)");
        Map<String, Object> args = new HashMap<>();
        args.put("id", 0);
        s.bindMap(args);
        s.bindBean(new Keith());
        int insertCount = s.execute();
        assertThat(insertCount).isOne();
        Something something = h.createQuery("select id, name from something").mapToBean(Something.class).one();
        assertThat(something).isEqualTo(new Something(0, "Keith"));
    }

    public static class Keith {

        public String getName() {
            return "Keith";
        }
    }
}
