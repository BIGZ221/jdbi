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
package org.jdbi.v3.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jdbi.v3.core.junit5.H2DatabaseExtension;
import org.jdbi.v3.core.statement.DefaultStatementBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Oracle was getting angry about too many open cursors because of the large number of prepared statements being created and cached indefinitely.
 */
public class TestTooManyCursors {

    @RegisterExtension
    public H2DatabaseExtension h2Extension = H2DatabaseExtension.instance().withInitializer(H2DatabaseExtension.SOMETHING_INITIALIZER);

    @Test
    public void testFoo() {
        ConnectionFactory cf = () -> DriverManager.getConnection(h2Extension.getUri());
        ConnectionFactory errorCf = new ErrorProducingConnectionFactory(cf, 99);
        Jdbi db = Jdbi.create(errorCf);

        db.useHandle(handle -> {
            handle.setStatementBuilder(new DefaultStatementBuilder());
            for (int idx = 0; idx < 100; idx++) {
                assertThat(handle.createQuery("SELECT " + idx).mapTo(int.class).first()).isEqualTo(idx);
            }
        });
    }

    private static class ErrorProducingConnectionFactory implements ConnectionFactory {

        private final ConnectionFactory target;
        private final int connCount;

        ErrorProducingConnectionFactory(ConnectionFactory target, int i) {
            this.target = target;
            connCount = i;
        }

        @Override
        public Connection openConnection() throws SQLException {
            return ConnectionInvocationHandler.newInstance(target.openConnection(), connCount);
        }
    }

    private static class ConnectionInvocationHandler implements InvocationHandler {

        private final Connection connection;
        private final int numSuccessfulStatements;
        private int numStatements = 0;

        static Connection newInstance(Connection connection, int numSuccessfulStatements) {
            return (Connection) Proxy.newProxyInstance(connection.getClass().getClassLoader(),
                new Class[]{Connection.class},
                new ConnectionInvocationHandler(connection, numSuccessfulStatements));
        }

        ConnectionInvocationHandler(Connection connection, int numSuccessfulStatements) {
            this.connection = connection;
            this.numSuccessfulStatements = numSuccessfulStatements;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                if ("createStatement".equals(method.getName())
                    || "prepareCall".equals(method.getName())
                    || "prepareStatement".equals(method.getName())) {
                    if (++numStatements > numSuccessfulStatements) {
                        throw new SQLException("Fake 'maximum open cursors exceeded' error");
                    }
                    return StatementInvocationHandler.newInstance((Statement) method.invoke(connection, args), this);
                } else {
                    return method.invoke(connection, args);
                }
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        }

        void registerCloseStatement() {
            numStatements--;
        }
    }

    private static class StatementInvocationHandler implements InvocationHandler {

        private final Statement stmt;
        private final ConnectionInvocationHandler connectionHandler;

        static Statement newInstance(Statement stmt, ConnectionInvocationHandler connectionHandler) {

            Class<?> o = stmt.getClass();
            List<Class<?>> interfaces = new ArrayList<>();
            while (!o.equals(Object.class)) {
                interfaces.addAll(Arrays.asList(o.getInterfaces()));
                o = o.getSuperclass();
            }

            return (Statement) Proxy.newProxyInstance(stmt.getClass().getClassLoader(),
                interfaces.toArray(new Class[0]),
                new StatementInvocationHandler(stmt, connectionHandler));
        }

        StatementInvocationHandler(Statement stmt, ConnectionInvocationHandler connectionHandler) {
            this.stmt = stmt;
            this.connectionHandler = connectionHandler;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("close".equals(method.getName())) {
                connectionHandler.registerCloseStatement();
            }
            try {
                return method.invoke(stmt, args);
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        }
    }
}
