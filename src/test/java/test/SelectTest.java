package test;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import orm.Orm;
import orm.Session.Session;
import orm.db.Db;
import orm.operate.Executor;
import orm.select.*;
import test.model.OM;
import test.model.OO;
import test.model.Obj;
import test.model.Ptr;

import java.util.ArrayList;

/**
 * Created by Administrator on 2017/7/10.
 */
public class SelectTest {
    private static Db db;

    @SuppressWarnings("Duplicates")
    @Before
    public void before() {
        ArrayList<String> clazzList = new ArrayList<String>();
        clazzList.add("test.model.Obj");
        clazzList.add("test.model.Sub");
        clazzList.add("test.model.Ptr");
        clazzList.add("test.model.OO");
        clazzList.add("test.model.OM");
        clazzList.add("test.model.Ign");
        Orm.init(clazzList.toArray(new String[0]));
        db = openDb();
        db.rebuild();
    }

    private Db openDb() {
        return Orm.openDb("localhost", 3306, "root", "root", "test");
    }

    @Test
    public void testCount() {
        Session session = db.openSession();
        Obj obj = new Obj();
        obj.setName("name");
        obj.setPtr(new Ptr());
        obj.setOo(new OO());
        obj.setOm(new OM[]{new OM(), new OM()});
        obj = Orm.convert(obj);
        Executor ex = Executor.createInsert(obj);
        ex.insert("ptr");
        ex.insert("oo");
        ex.insert("om");
        session.execute(ex);

        RootSelector<OM> rs = Selector.createSelect(OM.class);
        FieldSelector<Long> count = rs.count(Long.class);
        Long[] res = (Long[]) Selector.query(count, db.openConnection());
        Assert.assertEquals(res.length, 1);
        Assert.assertEquals(res[0].longValue(), 2);
    }

    @Test
    public void testMultiTarget() {
        Session session = db.openSession();
        Obj obj = new Obj();
        obj.setName("name");
        obj.setPtr(new Ptr());
        obj.setOo(new OO());
        obj.setOm(new OM[]{new OM(), new OM()});
        obj = Orm.convert(obj);
        Executor ex = Executor.createInsert(obj);
        ex.insert("ptr");
        ex.insert("oo");
        ex.insert("om");
        session.execute(ex);

        RootSelector<Obj> rs = Selector.createSelect(Obj.class);
        EntitySelector<OM> s1 = rs.get("om", OM.class);
        System.out.println(s1.parent());
        Object[][] res = Selector.query(new TargetSelector[]{rs, s1}, db.openConnection());
        Assert.assertEquals(res.length, 2);
        Assert.assertEquals(((Obj) (res[0][0])).getName(), "name");
        Assert.assertEquals(((Obj) (res[1][0])).getName(), "name");
        Assert.assertEquals(((Obj) (res[0][0])).getId().longValue(), 1);
        Assert.assertEquals(((Obj) (res[1][0])).getId().longValue(), 1);
        Assert.assertEquals(((OM) (res[0][1])).getId().longValue(), 1);
        Assert.assertEquals(((OM) (res[1][1])).getId().longValue(), 2);
    }
}
