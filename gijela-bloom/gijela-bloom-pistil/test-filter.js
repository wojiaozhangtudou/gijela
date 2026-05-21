// 测试部门过滤逻辑
const mockData = [
  {
    'id': 1,
    'parentId': 0,
    'name': '总部',
    'leader': null,
    'phone': null,
    'email': null,
    'sort': 0,
    'status': null,
    'createTime': null,
    'children': null
  },
  {
    'id': 2,
    'parentId': 0,
    'name': '沈阳分公司',
    'leader': null,
    'phone': null,
    'email': null,
    'sort': 2,
    'status': null,
    'createTime': null,
    'children': null
  }
];

// 模拟flattenTree函数
function flattenTree(tree) {
  const result = [];
  function traverse(nodes) {
    nodes.forEach(node => {
      result.push(node);
      if (node.children && node.children.length > 0) {
        traverse(node.children);
      }
    });
  }
  traverse(tree);
  return result;
}

// 测试新的过滤逻辑
const deptOptions = flattenTree(mockData).filter((dept) => dept.status === '1' || dept.status === null || dept.status === '0');

console.log('过滤后的部门选项:');
deptOptions.forEach(dept => {
  console.log(`  - ${dept.name} (ID: ${dept.id}, Status: ${dept.status})`);
});
console.log(`总共 ${deptOptions.length} 个选项`);
