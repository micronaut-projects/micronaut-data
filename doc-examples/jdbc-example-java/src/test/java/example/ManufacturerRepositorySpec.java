package example;

abstract class ManufacturerRepositorySpec {

    protected final ManufacturerRepository manufacturerRepository;

    public ManufacturerRepositorySpec(ManufacturerRepository manufacturerRepository) {
        this.manufacturerRepository = manufacturerRepository;
    }

    abstract void testMockRepo();
}
